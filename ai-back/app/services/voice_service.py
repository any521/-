"""services.voice_service · F7 语音碳管家（文本→意图槽位；TEXT_INPUT 兜底）"""
from __future__ import annotations

import re

from app.core import llm_router
from app.core.llm_router import AiRouteExhausted
from app.core.logging import get_logger
from app.schemas.voice import VoiceParseRequest, VoiceParseResponse, VoiceSlots

log = get_logger("voice_service")

_PROMPT = (
    "你是「点废成金」平台的碳管家语音助手，面向居民解答回收相关问题并辅助语音下单。\n"
    "【作答要求】\n"
    "1. 优先使用用户消息中的【数据资料】（真实回收知识库检索结果）作答："
    "品类、回收规则、参考价均可引用；资料不足时用通用常识简短补充，禁止编造具体价格数字。\n"
    "2. 回复用 Markdown 排版：可用 **加粗**、- 列表、> 引用、`行内代码`；"
    "禁止输出 HTML、禁止代码围栏；口语化、段落短，一次回复控制在 150 字以内（中文）。\n"
    "【意图与槽位】只输出一个 JSON 对象："
    '{"intent":"BOOK_RECYCLING|QUERY_ORDER|QUERY_CARBON_ACCOUNT|CANCEL_ORDER|SMALL_TALK|UNKNOWN",'
    '"slots":{"category":..,"appoint_time":..,"address":..},"confidence":0-1,"reply_text":..}\n'
    "想预约/上门/卖废品 → intent=BOOK_RECYCLING 并填 slots（品类/时间/地址）；"
    "查订单/积分/碳账户 → QUERY_ORDER / QUERY_CARBON_ACCOUNT；"
    "闲聊与知识问答 → SMALL_TALK。reply_text 即最终展示文案（Markdown 排版）。\n"
    "【品类枚举】BOOK_RECYCLING 时 slots.category 只能取以下之一（输出中文简称，禁止英文/拼音/其他写法）："
    "纸类、塑料、金属、织物、家电、电子、玻璃、厨余；其余意图 category 留空。"
)

# 预约回收意图的必填槽位（缺失时驱动多轮追问）
_REQUIRED_SLOTS = {"BOOK_RECYCLING": ["category", "appoint_time"]}

# ==================== RAG 检索接入（RAG-03 F7，契约 C2；RAG-02 未交付/失败一律静默降级） ====================
try:
    from app.rag import retriever
    _RAG_OK = True
except Exception:          # 模块缺失（RAG-02 未交付）或依赖缺失
    retriever = None
    _RAG_OK = False

# ==================== 联网搜索接入（可选知识源；未配置 key/失败一律静默降级） ====================
try:
    from app.services import web_search
    _WEB_OK = True
except Exception:
    web_search = None
    _WEB_OK = False

# 数据集标准子类/品类（对齐 data/*.json：可回收物 7 子类 + 电子废弃物 7 子类 + 厨余垃圾；命中即原值）
_DS_CATEGORIES = {
    "纸类", "塑料类", "金属类", "纺织类", "玻璃类", "木制品类", "橡胶类",
    "办公设备", "家电类", "小家电类", "数码类", "网络设备", "计算机类", "通讯设备",
    "厨余垃圾",
}

# keyword_search 命中归一化的最低 score（契约 C2：score 为 BM25 打分）
_MIN_KEYWORD_SCORE = 0.3

# 价格意图正则（用户话语含价格词或历史追问时，category 已确定则附参考价）
_PRICE_RE = re.compile(r"多少钱|价格|报价|单价|回收价|值多少")


def _rag_search(query: str, top_k: int = 1) -> list[dict]:
    """检索封装（voice 用 keyword_search）：失败/未就绪一律返回空，不影响主流程；命中进结构化日志"""
    if not _RAG_OK:
        return []
    try:
        hits = retriever.keyword_search(query, top_k=top_k)
        if hits:
            log.info(f"RAG 命中 top={[h['name'] for h in hits[:3]]} data_source={hits[0]['data_source']}")
        return hits
    except Exception as e:
        log.warning(f"RAG 检索降级: {e}")
        return []


def _rag_context(query: str, top_k: int = 4, filters: dict | None = None) -> str:
    """RAG 知识上下文（LLM 回答依据）：hybrid 失败降级 keyword；无命中返回空串"""
    if not _RAG_OK:
        return ""
    try:
        try:
            hits = retriever.hybrid_search(query, top_k=top_k, filters=filters)
        except Exception:
            hits = retriever.keyword_search(query, top_k=top_k, filters=filters)
    except Exception as e:
        log.warning(f"F7 语音 RAG 上下文降级: {e}")
        return ""
    if not hits:
        return ""
    log.info(f"F7 语音 RAG 上下文命中 top={[h['name'] for h in hits[:3]]}")
    lines = []
    for i, h in enumerate(hits, 1):
        parts = []
        name = (h.get("sub_category") or h.get("name") or "").strip()
        cat = (h.get("category") or "").strip()
        if cat and name and name != cat:
            name = f"{cat}·{name}"
        if name:
            parts.append(name)
        unit = h.get("price_unit") or "元/公斤"
        if h.get("min_price") is not None and h.get("max_price") is not None:
            parts.append(f"参考价 {h.get('min_price')}~{h.get('max_price')} {unit}")
        desc = (h.get("description") or "").strip().replace("\n", " ")
        if len(desc) > 150:
            desc = desc[:150] + "…"
        if desc:
            parts.append(desc)
        if parts:
            lines.append(f"{i}. " + " | ".join(parts))
    return "\n".join(lines)


def _price_lookup(category: str) -> dict | None:
    """真实基准价检索：失败/未就绪返回 None；命中进结构化日志"""
    if not _RAG_OK:
        return None
    try:
        hit = retriever.price_lookup(category=category)
        if hit:
            log.info(f"RAG 基准价命中 category={hit.get('category')} "
                     f"price={hit.get('min_price')}~{hit.get('max_price')} "
                     f"data_source={hit.get('data_source')}")
        return hit
    except Exception as e:
        log.warning(f"RAG 基准价检索降级: {e}")
        return None


def _normalize_category(category: str | None) -> str | None:
    """category 槽位归一化：对齐数据集标准子类；未命中/低分保持原值"""
    if not category or category in _DS_CATEGORIES:
        return category
    hits = _rag_search(category, top_k=1)
    if hits:
        h = hits[0]
        if (h.get("score") or 0) >= _MIN_KEYWORD_SCORE:
            normalized = (h.get("sub_category") if h.get("category") == "可回收物"
                          else h.get("category"))
            if normalized:
                log.info(f"F7 品类归一化 {category} → {normalized}")
                return normalized
    return category


def _format_price_note(hit: dict) -> str:
    """价格附注（Markdown 引用块）：只影响 reply_text 文本，不动 slots/intent 结构"""
    unit = hit.get("price_unit") or "元/公斤"
    date = hit.get("base_price_date") or "未知"
    return (f"\n> 参考价：**{hit.get('min_price')}~{hit.get('max_price')} {unit}**"
            f"（真实基准价，采集日期 {date}，价格可能波动）")


def _web_price_snippet(category: str) -> str:
    """联网行情片段：价格意图时补充近期市场行情（未配置 key/失败自然降级，仅附加文本）"""
    if not _WEB_OK:
        return ""
    try:
        hits = web_search.search(f"{category} 回收价格 行情")
        if not hits:
            return ""
        log.info(f"F7 语音联网行情命中 category={category} hits={len(hits)}")
        lines = []
        for h in hits[:2]:
            title = (h.get("title") or "")[:40]
            snippet = (h.get("snippet") or "")[:60]
            url = h.get("url") or ""
            lines.append(f"{title}：{snippet}" + (f"（{url}）" if url else ""))
        return "\n（近期行情参考）" + "\n".join(lines)
    except Exception as e:
        log.warning(f"F7 语音联网行情降级: {e}")
        return ""


class _LlmVoice(VoiceParseResponse):
    """LLM 出参与响应同构，need_slots/model 信息由 service 补充"""


# ==================== V44 图片扫描接入（YOLO；模型缺失/失败一律静默降级，不阻断主流程） ====================
try:
    from app.schemas.yolo import YoloDetectRequest
    from app.services import yolo_service
    _YOLO_OK = True
except Exception:          # yolo 模块未交付或依赖缺失
    yolo_service = None
    _YOLO_OK = False

# YOLO 类别 → (RAG 检索关键词, 品类限定)（端侧/云端同款 6 类）
# 品类限定：图片识别只在该大类内检索，避免"塑料"等宽泛词命中其他类目
# （如其他垃圾下的打火机 material 含"塑料"，数百条变体淹没可回收塑料条目）
_YOLO_CLASS_QUERY = {
    "glass": ("玻璃瓶 废玻璃", "可回收物"),
    "metal": ("金属 易拉罐 废铁", "可回收物"),
    "paper": ("纸箱 废纸 报纸", "可回收物"),
    "plastic": ("塑料瓶 塑料膜 废塑料", "可回收物"),
    "kitchen": ("剩饭 果皮 厨余", "厨余垃圾"),
    "other": ("一次性用品 其他垃圾", "其他垃圾"),
}

# voice 场景检测阈值：略低于端侧默认 0.35，提升长焦/复杂背景/小目标召回
_VOICE_SCORE_THRESHOLD = 0.25

# 未检测到目标时的 LLM 上下文（说明事实 + 引导要求，避免生硬回复"无法识别图片"）
_NO_DETECT_HINT = (
    "YOLO 模型未检测到任何垃圾/可回收目标（可能原因：物品不属于常见的"
    "玻璃/金属/纸/塑料/厨余/其他 6 类、拍摄模糊或角度不佳）。"
    "请友好告知用户图片中暂未识别到目标，引导其重新拍摄更清晰、正对物品的照片，"
    "或直接描述物品名称/材质，辅助判断是否可回收；不要回复『无法识别图片』这类生硬话术。"
)


def _scan_image(image_base64: str) -> tuple[str | None, list[dict], str]:
    """图片扫描：YOLO 检测 → (标注图 base64, 检测明细, 文本上下文)。

    模型缺失/推理异常一律静默降级：返回 (None, [], "")，不影响文本对话主流程；
    命中时把类别命中检索结果追加为【图片识别资料】，供 LLM 结合知识库作答；
    未命中任何目标时仍返回引导上下文，避免 LLM 因缺图而回复"无法识别图片"。
    """
    if not _YOLO_OK:
        return None, [], ""
    try:
        resp = yolo_service.detect_image(YoloDetectRequest(
            image_base64=image_base64, score_threshold=_VOICE_SCORE_THRESHOLD))
    except ValueError as e:
        log.warning(f"F7 语音图片扫描入参降级: {e}")
        return None, [], ""
    except Exception as e:  # noqa: BLE001（模型缺失/推理异常）
        log.warning(f"F7 语音图片扫描降级: {e}")
        return None, [], ""
    items = []
    for d in resp.detections:
        items.append({
            "class_name": d.class_name, "confidence": d.confidence, "bbox": d.bbox,
        })
    if not items:
        return resp.annotated_image_base64, [], _NO_DETECT_HINT
    # 检测类别 → 知识库检索：每个类别按 (关键词, 品类) 各自限定检索再合并，
    # 避免"塑料"等宽泛词命中其他类目（如打火机 material 含"塑料"，数百条变体淹没可回收条目）
    rag_hits = ""
    if _YOLO_OK:
        seen, parts = set(), []
        for d in items:
            kw, cat = _YOLO_CLASS_QUERY.get(d["class_name"], (d["class_name"], None))
            filters = {"category": cat} if cat else None
            try:
                chunk = _rag_context(kw, top_k=2, filters=filters)
            except TypeError:
                chunk = _rag_context(kw, top_k=2)
            for line in chunk.splitlines():
                if line.strip() and line.strip() not in seen:
                    seen.add(line.strip())
                    parts.append(line)
        rag_hits = "\n".join(parts)
    lines = ["、".join(
        f"{_YOLO_CLASS_QUERY.get(d['class_name'], (d['class_name'],))[0]}"
        f"({d['confidence']:.0%})" for d in items)]
    if rag_hits:
        lines.append(f"【图片识别资料】（知识库检索结果，仅作参考）\n{rag_hits}")
    log.info(f"F7 语音图片扫描 hits={len(items)} classes={[d['class_name'] for d in items]}")
    return resp.annotated_image_base64, items, "\n".join(lines)


def parse(req: VoiceParseRequest) -> VoiceParseResponse:
    history = "\n".join(f"{m.get('role')}: {m.get('text')}" for m in req.context[-6:])
    rag = _rag_context(req.text)
    # V44：携带图片 → 先做 YOLO 检测，标注图/明细回传前端，识别上下文进 LLM
    annotated, detections, image_ctx = _scan_image(req.image_base64) if req.image_base64 else (None, [], "")
    user_content = f"历史对话:\n{history or '无'}\n"
    if image_ctx:
        if detections:
            user_content += (f"\n【图片识别】用户上传了一张垃圾图片，"
                             f"YOLO 模型给出的检测类别与置信度是第一事实，"
                             f"优先据此作答；知识库检索资料仅作参考，"
                             f"若与检测类别矛盾以检测类别为准。\n{image_ctx}\n")
        else:
            # 无检测命中：上下文为引导说明，LLM 据此友好引导而非生硬拒绝
            user_content += f"\n【图片识别】用户上传了一张图片。\n{image_ctx}\n"
    if rag:
        user_content += f"\n【数据资料】（真实回收知识库检索结果，优先据此回答）\n{rag}\n"
    user_content += f"\n本轮话语: {req.text}"
    try:
        result = llm_router.invoke(
            capability="voice_parse",
            messages=[
                {"role": "system", "content": _PROMPT},
                {"role": "user", "content": user_content},
            ],
            response_model=_LlmVoice,
            context={"text": req.text},
        )
        parsed: _LlmVoice = result.parsed
        slots = parsed.slots
        # RAG-03 F7 品类槽位归一化：对齐数据集标准子类（未命中保持原值）
        if slots.category:
            slots.category = _normalize_category(slots.category)
        need = [s for s in _REQUIRED_SLOTS.get(parsed.intent, [])
                if getattr(slots, s, None) is None]
        if need:
            names = {"category": "回收品类", "appoint_time": "预约时间", "address": "地址"}
            parsed.reply_text = "请补充：" + "、".join(names[s] for s in need)
        else:
            # RAG-03 F7 价格类回答增强（低优先）：话语/历史含价格意图且 category 已确定 → 附参考价
            price_intent = bool(_PRICE_RE.search(req.text)) or any(
                m.get("role") == "user" and _PRICE_RE.search(m.get("text", ""))
                for m in req.context[-6:])
            if price_intent and slots.category:
                hit = _price_lookup(slots.category)
                if hit:
                    parsed.reply_text += _format_price_note(hit)
                parsed.reply_text += _web_price_snippet(slots.category)
        return VoiceParseResponse(
            intent=parsed.intent, slots=slots, confidence=parsed.confidence,
            reply_text=parsed.reply_text, need_slots=need,
            model_name=f"{result.provider}/{result.model}", degraded=result.degraded,
            annotated_image_base64=annotated, detections=detections)
    except AiRouteExhausted:
        log.warning("F7 路由链耗尽，回退 TEXT_INPUT")
        return VoiceParseResponse(
            intent="UNKNOWN", slots=VoiceSlots(), confidence=0.0,
            reply_text="语音服务暂不可用，请使用文字或表单下单",
            need_slots=[], model_name="fallback/text-input", degraded=True,
            annotated_image_base64=annotated, detections=detections)

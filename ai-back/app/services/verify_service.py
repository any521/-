"""services.verify_service · F1 视觉审核（识别 + 阈值决策建议）

三层管线中本服务承担第 2/3 层：视觉大模型识别 → 阈值决策建议。
阈值可被请求方（Java 编排层，读 sys_config）覆盖；AI 链路耗尽时抛
AiRouteExhausted，由 router 返回 3001，Java 侧转人工审核队列。
"""
from __future__ import annotations

import time

from app.core import llm_router
from app.core.logging import get_logger
from app.schemas.verify import VerifyImageRequest, VerifyImageResponse, VisionOutput

log = get_logger("verify_service")

# 默认阈值（sys_config: ai.verify.pass_threshold / review_threshold 可覆盖）
PASS_THRESHOLD = 0.85
REVIEW_THRESHOLD = 0.60

_PROMPT = (
    "你是垃圾分类审核专家。请识别图片中的可回收物/厨余垃圾，输出 JSON："
    '{"category":品类,"purity":纯净度0-1,"confidence":置信度0-1,'
    '"violations":[违规项],"description":说明}。'
    "category 必须且只能输出以下之一：废纸类、塑料类、金属类、织物类、家电类、电子产品、玻璃类、厨余垃圾；"
    "若物品明显属于某个具体品类则输出该品类，不确定时选最接近的一项，不要输出「可回收物」「垃圾」等大类。"
    "违规项包括：有害/违禁物品、明显造假摆拍、与垃圾无关图片。"
)

# ==================== RAG 检索接入（RAG-03 F1，契约 C2；RAG-02 未交付/失败一律静默降级） ====================
try:
    from app.rag import retriever
    _RAG_OK = True
except Exception:          # 模块缺失（RAG-02 未交付）或依赖缺失
    retriever = None
    _RAG_OK = False

# LLM 输出合法品类枚举（prompt 冻结，8 类；纠偏目标集合）
_VALID_CATEGORIES = {"废纸类", "塑料类", "金属类", "织物类", "家电类", "电子产品", "玻璃类", "厨余垃圾"}

# 数据集标准子类/品类 → LLM 8 类枚举（数据集用「纸类/纺织类/数码类」等，纠偏时桥接回枚举）
_DS_TO_LLM = {
    "纸类": "废纸类", "塑料类": "塑料类", "金属类": "金属类", "纺织类": "织物类",
    "玻璃类": "玻璃类", "家电类": "家电类", "小家电类": "电子产品", "数码类": "电子产品",
    "计算机类": "电子产品", "通讯设备": "电子产品", "网络设备": "电子产品", "办公设备": "电子产品",
    "厨余垃圾": "厨余垃圾",
}

# 先验注入模板（检索命中后追加到 system prompt 之后；仅作先验，禁止照抄）
_PRIOR_HINT = "【参考数据（来源于真实回收数据集，仅作先验，禁止照抄）】\n{prior}"


def _rag_search(query: str, top_k: int = 5, min_confidence: float = 0.0,
                mode: str = "hybrid") -> list[dict]:
    """检索封装：失败/未就绪一律返回空，不影响主流程；命中进结构化日志"""
    if not _RAG_OK:
        return []
    try:
        fn = retriever.hybrid_search if mode == "hybrid" else retriever.keyword_search
        hits = fn(query, top_k=top_k, min_confidence=min_confidence)
        if hits:
            log.info(f"RAG 命中 top={[h['name'] for h in hits[:3]]} data_source={hits[0]['data_source']}")
        return hits
    except Exception as e:
        log.warning(f"RAG 检索降级: {e}")
        return []


def _build_prior(hits: list[dict]) -> str:
    """top5 命中的 name/material/form 去重前 8 项，构造一行先验（类名取数据集枚举）"""
    names, forms, materials = [], [], []
    for h in hits:
        for field, bucket in (("name", names), ("form", forms), ("material", materials)):
            v = (h.get(field) or "").strip()
            if v and v not in bucket:
                bucket.append(v)
    head = hits[0]
    label = (head.get("sub_category") or head.get("category") or "")
    parts = [f"{label}常见物品={'、'.join(names[:8])}"]
    if forms:
        parts.append(f"常见形态={'、'.join(forms[:8])}")
    if materials:
        parts.append(f"常见材质={'、'.join(materials[:8])}")
    return "参考数据：" + "；".join(parts)


def _correct_category(vision: VisionOutput) -> None:
    """品类校验纠偏：LLM 输出不在 8 类合法枚举时，检索数据集标准子类回填；仍无效保持原值"""
    if vision.category in _VALID_CATEGORIES:
        return
    hits = _rag_search(vision.category, top_k=1, min_confidence=0.5, mode="keyword")
    if not hits:
        return
    hit = hits[0]
    # 可回收物取 sub_category（如「纸类」）；电子废弃物取 sub_category（如「家电类」）；其余取 category（厨余→厨余垃圾）
    if hit.get("category") in ("可回收物", "电子废弃物"):
        ds = hit.get("sub_category")
    else:
        ds = hit.get("category")
    corrected = _DS_TO_LLM.get(ds or "")
    if corrected in _VALID_CATEGORIES:
        log.info(f"F1 品类纠偏 {vision.category} → {corrected} (ds={ds})")
        vision.category = corrected


def verify_image(req: VerifyImageRequest,
                 pass_threshold: float = PASS_THRESHOLD,
                 review_threshold: float = REVIEW_THRESHOLD) -> VerifyImageResponse:
    # RAG-03 F1 先验检索：invoke 前执行一次（耗时不计入 elapsed_ms）；未命中与现状完全一致
    prior = ""
    hits = _rag_search(req.claimed_category or "可回收物", top_k=5, min_confidence=0.5)
    if hits:
        prior = _build_prior(hits)
    system_content = _PROMPT + (_PRIOR_HINT.format(prior=prior) if prior else "")

    start = time.perf_counter()
    result = llm_router.invoke(
        capability="visual_verify",
        messages=[
            {"role": "system", "content": system_content},
            {"role": "user", "content": [
                {"type": "text", "text": f"申报品类：{req.claimed_category or '未申报'}"},
                {"type": "image_url", "image_url": {"url": req.image_url}},
            ]},
        ],
        response_model=VisionOutput,
        context=req.model_dump(),
    )
    vision: VisionOutput = result.parsed
    elapsed_ms = int((time.perf_counter() - start) * 1000)

    # RAG-03 F1 品类校验纠偏：LLM 输出不在 8 类合法枚举时，用检索命中记录回填（无命中保持原值）
    _correct_category(vision)

    # 阈值决策（对齐 memory.md F1 业务规则）
    if vision.violations:
        suggestion = "REJECT"
    elif vision.confidence >= pass_threshold:
        suggestion = "AUTO_PASS"
    elif vision.confidence >= review_threshold:
        suggestion = "MANUAL_REVIEW"
    else:
        suggestion = "MANUAL_REVIEW"

    category_match = (req.claimed_category is None
                      or vision.category == req.claimed_category)
    if not category_match and suggestion == "AUTO_PASS":
        suggestion = "MANUAL_REVIEW"  # 品类不一致不允许自动通过

    log.info(f"F1 verify biz={req.biz_type}:{req.biz_id} model={result.model} "
             f"conf={vision.confidence:.2f} suggestion={suggestion} elapsed={elapsed_ms}ms")
    return VerifyImageResponse(
        suggestion=suggestion,
        category=vision.category,
        category_match=category_match,
        purity=vision.purity,
        confidence=vision.confidence,
        violations=vision.violations,
        description=vision.description,
        model_name=f"{result.provider}/{result.model}",
        degraded=result.degraded,
        elapsed_ms=elapsed_ms,
    )

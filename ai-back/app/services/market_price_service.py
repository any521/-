"""services.market_price_service · 回收市场行情（联网报价，博查/Tavily 走 web_search）

链路：联网搜索「城市 + 品类 + 回收价格」→ LLM 从搜索结果提炼当日报价
（market_price 能力，路由链）→ 正则提取兜底。搜索无结果/结果无关 → price_per_kg=None，
Java 侧对该品类标 0（不可回收品类如厨余垃圾由 Java 直接标 0，不联网）。

品类防护（2026-08-10 修复：废铝「22300元/吨」被正则兜底误当废纸「22.3元/kg」）：
- 品类合理区间 _CATEGORY_RULES：单位误读/无关行情超区间一律剔除（废纸/织物上限 5 元/kg）；
- 品类相关性关键词：搜索结果不含品类词（如查询废纸返回铝业采购价）整条丢弃；
- 搜索词规范化 _SEARCH_TERM：废纸类→「废纸回收价格 元/吨」等精准行业词，提升召回相关性
  （2026-08-12：泛词「废纸 回收价格」被博查返回招标公告等无关页，相关性过滤 0/5）；
- LLM 提炼结果同样过品类区间，超区间丢弃改走正则兜底。

配置（web_search 既有通道）：
- 管理端「AI 供应商」name='web-search' 行 web_search_provider=bocha + api_key（博查密钥）
- 或环境变量 BOCHA_API_KEY（/app/services/web_search.py 的解析逻辑）
"""
from __future__ import annotations

import re

from pydantic import BaseModel

from app.core.llm_router import AiRouteExhausted, invoke as llm_invoke
from app.core.logging import get_logger
from app.schemas.market_price import MarketPriceRequest, MarketPriceResponse
from app.services import web_search

log = get_logger("market_price")

# 搜索结果中的价格形态（元/公斤、元每公斤、元/kg、元/吨、元/斤 等）
_PRICE_RES = [
    re.compile(r"(\d+(?:\.\d+)?)\s*元\s*(?:/|每)?\s*(?:公斤|千克|kg)\b", re.IGNORECASE),
    re.compile(r"(\d+(?:\.\d+)?)\s*元\s*(?:/|每)?\s*吨\b"),
    re.compile(r"(\d+(?:\.\d+)?)\s*元\s*(?:/|每)?\s*斤\b"),
]

# 通用「报价 X 元」形态（无单位后缀，需结合上下文判定单位）
_GENERIC_PRICE_RES = re.compile(r"(?:回收价|报价|价格|市场价|主流价)[^0-9]{0,12}(\d+(?:\.\d+)?)\s*(?:元|块)")

# 价格合理性上限（元/公斤）：超过视为单位误读（如 元/吨 未被识别），跳过该候选
_MAX_PRICE_KG = 200
# 价格合理性下限（元/公斤）：低于视为噪声（如「0.03元」常为按个/次的报价），跳过
_MIN_PRICE_KG = 0.05

# ============ 品类防护（2026-08-10 修复） ============
# 品类 → (合理区间(元/kg), 相关性关键词)。区间用于剔除单位误读（元/吨、元/斤换算失真）与
# 无关品类行情（废铝 22300元/吨=22.3元/kg 曾因无品类校验被误当废纸价落库）；
# 相关性关键词用于过滤搜索结果中与查询品类无关的页面（查询废纸返回铝业/废钢新闻整条丢弃）。
# 匹配：查询品类名包含任一关键词即命中（顺序优先）；未命中走全局兜底区间（自定义询价保持灵活）。
_CATEGORY_RULES: list[tuple[tuple[float, float], tuple[str, ...]]] = [
    # 废纸类：现实 0.3-3 元/kg（黄板纸约 2000-3000 元/吨）；PAPER/纸类/纸壳等别名
    ((0.1, 5.0), ("废纸", "纸类", "纸壳", "纸板", "黄板", "纸箱", "书本", "报纸", "瓦楞", "纸皮", "纸业", "paper")),
    # 织物类：旧衣物 0.3-2 元/kg
    ((0.1, 5.0), ("织物", "旧衣", "衣服", "服装", "布料", "鞋类", "鞋子")),
    # 塑料类：混合废塑料 1-8 元/kg
    ((0.1, 12.0), ("塑料", "pet", "pe", "pp", "饮料瓶", "矿泉水瓶")),
    # 金属类：废铁 1-3 / 废铝 12-25 / 废铜 40-65 元/kg
    ((0.5, 90.0), ("金属", "废铁", "废钢", "废铜", "废铝", "不锈钢", "铜", "铝", "铁")),
    # 家电类：整机按重量折算 1-30 元/kg
    ((0.5, 40.0), ("家电", "电器", "冰箱", "洗衣机", "空调", "电视机")),
    # 电子产品：手机/主板等 10-200 元/kg
    ((1.0, 200.0), ("电子产品", "手机", "电脑", "主板", "芯片")),
    # 玻璃类：0.05-1 元/kg
    ((0.05, 2.0), ("玻璃",)),
]

# 品类 → 更贴近回收行业叫法的搜索词（提升召回相关性；未映射品类原样使用）
# 2026-08-12：改为带「元/吨」的精准行业词——泛词「废纸 回收价格」被博查返回
# 招标公告/机床回收广告等无关页（相关性过滤 0/5），带单位词直接召回行情表/回收平台报价页。
_SEARCH_TERM = {
    "废纸类": "废纸回收价格 元/吨", "纸类": "废纸回收价格 元/吨", "PAPER": "废纸回收价格 元/吨",
    "织物类": "旧衣服回收价格 元/吨", "FABRIC": "旧衣服回收价格 元/吨",
    "塑料类": "废塑料回收价格 元/吨", "PLASTIC": "废塑料回收价格 元/吨",
    "金属类": "废金属回收价格 元/吨", "METAL": "废金属回收价格 元/吨",
    "家电类": "废旧家电回收价格 元/公斤",
    "电子产品": "废旧电子产品回收价格 元/公斤", "E_WASTE": "废旧电子产品回收价格 元/公斤",
    "玻璃类": "废玻璃回收价格 元/吨",
}


def _rule_for(category: str) -> tuple[tuple[float, float], tuple[str, ...]] | None:
    """命中品类防护规则；自定义品类（无关键词命中）返回 None（不设限）"""
    c = category.lower()
    for rng, keywords in _CATEGORY_RULES:
        if any(k in c for k in keywords):
            return rng, keywords
    return None


def _price_range(category: str) -> tuple[float, float]:
    """品类合理区间（元/kg）；未命中规则用全局兜底区间"""
    rule = _rule_for(category)
    return rule[0] if rule else (_MIN_PRICE_KG, _MAX_PRICE_KG)


def _relevant(category: str, hit: dict[str, str]) -> bool:
    """搜索结果与查询品类是否相关：标题/摘要命中品类任一关键词即相关。
    标准品类严格校验；自定义品类不校验（保持原行为）。"""
    rule = _rule_for(category)
    if rule is None:
        return True
    text = f"{hit.get('title', '')} {hit.get('snippet', '')}".lower()
    return any(k in text for k in rule[1])


def _unit_of(matched: str, tail: str) -> str:
    """判定数字价格的单位：看匹配文本自身（如「2090元/吨」）与数字后 6 个字符。
    返回 kg/ton/jin/other/unknown"""
    context = matched.lower() + tail
    if re.search(r"公斤|千克|kg", context):
        return "kg"
    if "吨" in context:
        return "ton"
    if "斤" in context:
        return "jin"
    if re.search(r"[批个台套箱条只件桶袋]|/月|/日", tail):
        return "other"
    return "unknown"


# 价格形态匹配（数字前是否有价格语境/涨跌语境）
_PRICE_CTX_RE = re.compile(r"主流价|价格|回收价|报价|市场价|收购价|现价|行情|均价")
_CHANGE_CTX_RE = re.compile(r"涨|跌|幅|调")


# ============ LLM 提炼（2026-08-12 恢复：正则对表格型行情页/千分位漏提取，LLM 判定单位换算更准） ============
class _MarketPriceLLM(BaseModel):
    """LLM 提炼出参：搜索结果 → 品类当日回收单价（元/公斤）；无法确定输出 null"""
    price_per_kg: float | None = None
    unit: str | None = None
    note: str | None = None
    source: str | None = None


_LLM_PROMPT = (
    "你是废品回收行情分析助手。根据下方搜索结果，提炼「{city}」{category}的当日回收价（元/公斤）。\n"
    "规则：\n"
    "1. 只输出一个 JSON 对象：{{\"price_per_kg\": 数字或null, \"unit\": \"元/公斤\", "
    "\"note\": \"依据说明（含换算与来源）\", \"source\": \"引用页面标题\"}}\n"
    "2. 单位换算：元/吨 ÷1000、元/斤 ×2、元/公斤直接用；多档价格（最低价/最高价）取中间值；"
    "表格型行情（如「废纸箱 1750 1850」+ 上文单位:元/吨）按表格单位换算\n"
    "3. 数字四舍五入保留 2 位小数；无明确单位或无法判断时 price_per_kg 输出 null\n"
    "4. 结果页地域不符（如查询郑州返回外地行情）时仍可采信（全国行情近似），note 中注明来源地域\n"
    "5. 搜索结果中若无该品类真实价格信息，price_per_kg 必须输出 null\n"
    "搜索结果如下：\n{results}"
)


def _llm_extract(hits: list[dict[str, str]], category: str, city: str) -> _MarketPriceLLM | None:
    """LLM 提炼（capability=market_price 路由链：deepseek-chat → mock）；
    mock 降级结果弃用（假价），返回 None 由正则兜底；网络/超时/校验失败同样降级。"""
    results = "\n".join(
        f"- [{h.get('title', '')}]({h.get('url', '')})：{(h.get('snippet') or '')[:500]}"
        for h in hits[:5])
    try:
        r = llm_invoke(
            "market_price",
            [{"role": "user", "content": _LLM_PROMPT.format(city=city, category=category, results=results)}],
            _MarketPriceLLM,
            context={"category": category},
            temperature=0.2,
            timeout_seconds=20,
        )
    except AiRouteExhausted:
        return None
    if r.degraded:
        log.info(f"市场行情 LLM 提炼为 mock 降级，弃用走正则 category={category}")
        return None
    return r.parsed


def _regex_extract(hits: list[dict[str, str]], category: str) -> float | None:
    """正则兜底：从搜索结果片段提取回收价（统一换算为 元/公斤）。
    - 先按品类相关性过滤结果（查询废纸时丢弃铝业/废钢等无关页面，见 _relevant）；
    - 元/吨 ÷1000、元/斤 ×2；候选统一过滤到品类合理区间 [lo, hi]（废纸/织物上限 5，
      自定义品类走全局 [0.05, 200]；如「涨幅10-30元/吨」换算后 0.01~0.03 直接被下限剔除）；
    - 数字前 20 字出现 涨/跌/幅/调 视为变动幅度而非报价（如「上调30元/吨」），跳过；
    - 元/吨、元/斤 候选若前 30 字无价格语境词（主流价/报价/价格…），要求换算后
      ≥0.5 元/公斤 才采信（如「天津重废持稳于2100元/吨」）；元/公斤 无需额外语境；
    - 显式单位（优先级 3）优先于通用「X元」形态（优先级 1）；跨所有结果取最高优先级，
      不按第一个命中即返回。
    """
    def _guarded(raw: float, unit: str, value: float, start: int,
                 lo: float, hi: float) -> float | None:
        if not (lo <= value <= hi):
            return None
        prev20 = text[max(0, start - 20):start]
        if _CHANGE_CTX_RE.search(prev20) and not _PRICE_CTX_RE.search(prev20):
            return None
        if unit in ("ton", "jin") and not _PRICE_CTX_RE.search(text[max(0, start - 30):start]):
            if value < 0.5:
                return None
        return round(value, 2)

    lo, hi = _price_range(category)
    relevant = [h for h in hits if _relevant(category, h)]
    if len(relevant) != len(hits):
        log.info(f"市场行情品类相关性过滤 category={category} 保留 {len(relevant)}/{len(hits)} 条")
    best: tuple[int, float] | None = None
    for h in relevant:
        text = f"{h.get('title', '')} {h.get('snippet', '')}"
        # 千分位归一：「回收价格: 1,500元/吨」→「1500元/吨」，避免价格正则漏匹配
        text = re.sub(r"(?<=\d),(?=\d{3}\b)", "", text)
        for pattern in _PRICE_RES:
            for m in pattern.finditer(text):
                raw, unit = float(m.group(1)), _unit_of(m.group(0), text[m.end():m.end() + 6])
                if unit == "kg":
                    value = _guarded(raw, unit, raw, m.start(), lo, hi)
                elif unit == "ton":
                    value = _guarded(raw, unit, raw / 1000, m.start(), lo, hi)
                elif unit == "jin":
                    value = _guarded(raw, unit, raw * 2, m.start(), lo, hi)
                else:
                    value = None
                if value is not None and (best is None or 3 > best[0]):
                    best = (3, value)
        for m in _GENERIC_PRICE_RES.finditer(text):
            raw = float(m.group(1))
            value = _guarded(raw, "other", raw, m.start(), lo, hi)
            if value is not None and (best is None or 1 > best[0]):
                best = (1, value)
    return best[1] if best else None


def fetch_market_price(req: MarketPriceRequest) -> MarketPriceResponse:
    category = req.category.strip()
    city = req.city.strip() or "北京"

    term = _SEARCH_TERM.get(category, category)
    # 搜索词已带「元/吨」「元/公斤」等形态词（2026-08-12 精准化），不再重复拼「回收价格」尾巴；
    # 不加「今日」：实测博查带「今日」召回质量崩坏（招标/美股/广告），去掉后稳定命中废品网行情表
    hits = web_search.search(f"{city} {term}", count=5)

    # LLM 提炼优先（deepseek 判定单位/表格换算更准；mock 假价/失败 → 正则兜底）
    if hits:
        llm = _llm_extract(hits, category, city)
        if llm is not None and llm.price_per_kg is not None:
            lo, hi = _price_range(category)
            if lo <= llm.price_per_kg <= hi:
                log.info(f"市场行情 LLM 提炼 city={city} category={category} price={llm.price_per_kg}")
                return MarketPriceResponse(
                    category=category, price_per_kg=round(llm.price_per_kg, 2),
                    unit=llm.unit or "元/公斤", note=llm.note or "LLM 提炼（搜索结果）",
                    source=llm.source or (hits[0] or {}).get("title", ""), degraded=False)
            log.warning(f"市场行情 LLM 提炼超区间丢弃 category={category} price={llm.price_per_kg}"
                        f"（区间 {lo}-{hi}，走正则兜底）")

    price = _regex_extract(hits, category)
    if price is not None:
        log.info(f"市场行情正则提取 city={city} category={category} price={price}")
        return MarketPriceResponse(
            category=category, price_per_kg=price, note="搜索结果正则提取（非 LLM 提炼）",
            source=(hits[0] or {}).get("title", ""), degraded=True)

    # 搜索不到/结果无关 → price_per_kg=None（Java 侧保留旧价或跳过，不标 0）
    log.warning(f"市场行情无可用报价 city={city} category={category}"
                f"（搜索 hits={len(hits)} 或未检索到可解析报价）")
    return MarketPriceResponse(category=category, price_per_kg=None,
                               note="联网搜索无结果或未检索到可解析报价", degraded=True)

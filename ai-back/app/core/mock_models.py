"""core.mock_models · mock 供应商的确定性结构化输出

用途：Sprint 0 契约联调（业务线 Mock 先行）、降级演练、无密钥开发环境。
按 context 关键字触发不同分支（如图片 URL 含 violation/dirty），其余走哈希确定性采样。
"""
from __future__ import annotations

import hashlib
from typing import Any

_CATEGORIES = ["纸类", "塑料", "金属", "玻璃", "织物", "家电"]

# 语音口语词 → 回收品类（按序匹配，第一命中为准；长词优先）
_CATEGORY_WORDS = [
    ("纸类", ("废纸", "报纸", "纸箱", "纸板", "书本", "纸类")),
    ("塑料", ("塑料", "饮料瓶", "矿泉水瓶", "瓶子")),
    ("金属", ("金属", "易拉罐", "铁", "铝")),
    ("玻璃", ("玻璃", "酒瓶")),
    ("织物", ("旧衣服", "衣物", "衣服", "床单", "织物")),
    ("家电", ("冰箱", "洗衣机", "电视", "空调", "电脑", "家电")),
]


def _seed(context: dict[str, Any]) -> int:
    text = repr(sorted(context.items()))
    return int(hashlib.sha256(text.encode("utf-8")).hexdigest()[:8], 16)


def generate(capability: str, context: dict[str, Any]) -> dict[str, Any]:
    fn = _GENERATORS.get(capability)
    if fn is None:
        raise ValueError(f"mock 未支持能力: {capability}")
    return fn(context)


def _visual_verify(ctx: dict[str, Any]) -> dict[str, Any]:
    # 3D 构建场景（img2threejs 风格）：返回 Build3dSpec 形状，供无密钥环境联调 3D 链路
    if ctx.get("scene") == "3d_build":
        return {
            "item": "塑料水瓶",
            "primitives": [
                {"type": "cylinder", "radius": 0.28, "height": 0.68, "pos": [0, 0.34, 0],
                 "rot": [0, 0, 0], "color": "#eef2f8", "roughness": 0.4, "metalness": 0},
                {"type": "cylinder", "radius": 0.13, "height": 0.12, "pos": [0, 0.74, 0],
                 "rot": [0, 0, 0], "color": "#1e5aa8", "roughness": 0.5, "metalness": 0},
                {"type": "plane", "size": [1.6, 1.6], "pos": [0, 0, 0],
                 "rot": [-1.5708, 0, 0], "color": "#c8d8e8", "roughness": 0.9, "metalness": 0},
            ],
            "tick": "sway",
        }
    url = str(ctx.get("image_url", ""))
    seed = _seed(ctx)
    if "violation" in url or "hazard" in url:
        return {"category": "有害垃圾", "purity": 0.1, "confidence": 0.97,
                "violations": ["疑似有害/违禁物品"], "description": "检测到违禁投放物"}
    if "dirty" in url or "mixed" in url:
        return {"category": _CATEGORIES[seed % len(_CATEGORIES)], "purity": 0.45,
                "confidence": 0.62, "violations": ["纯净度不足，疑似混投"],
                "description": "识别到混合杂质，建议人工复核"}
    return {"category": _CATEGORIES[seed % len(_CATEGORIES)],
            "purity": 0.85 + (seed % 10) / 100, "confidence": 0.9 + (seed % 8) / 100,
            "violations": [], "description": "图片清晰，品类明确"}


def _repair_diagnose(ctx: dict[str, Any]) -> dict[str, Any]:
    desc = str(ctx.get("fault_desc", ""))
    seed = _seed(ctx)
    if any(k in desc for k in ("烧", "焦味", "冒烟", "进水")):
        return {"possible_faults": [{"fault": "主板短路/元件烧毁", "probability": 0.82}],
                "repairable_probability": 0.25,
                "suggested_parts": ["主板", "保险丝"],
                "estimated_hours": 3.0,
                "suggestion": "REPLACE_OR_RECYCLE",
                "reasoning": "描述含烧毁特征，维修成本高于残值，建议转回收"}
    return {"possible_faults": [
                {"fault": "电源模块故障", "probability": 0.55 + (seed % 20) / 100},
                {"fault": "接触不良/线路老化", "probability": 0.3}],
            "repairable_probability": 0.78,
            "suggested_parts": ["电源板", "排线"],
            "estimated_hours": 1.5,
            "suggestion": "ONSITE_REPAIR",
            "reasoning": "常见可修故障，建议技术员上门检测电源模块"}


def _voice_parse(ctx: dict[str, Any]) -> dict[str, Any]:
    text = str(ctx.get("text", ""))
    intent = "BOOK_RECYCLING"
    if any(k in text for k in ("积分", "账单", "碳")):
        intent = "QUERY_CARBON_ACCOUNT"
    elif any(k in text for k in ("订单", "进度", "到哪")):
        intent = "QUERY_ORDER"
    slots: dict[str, Any] = {"category": None, "appoint_time": None, "address": None}
    for cat, words in _CATEGORY_WORDS:
        if any(w in text for w in words):
            slots["category"] = cat
            break
    for k in ("明天上午", "明天下午", "今天下午", "今天上午", "周六", "周日", "明天", "今天"):
        if k in text:
            slots["appoint_time"] = k
            break
    return {"intent": intent, "slots": slots,
            "confidence": 0.9 if slots["category"] or intent != "BOOK_RECYCLING" else 0.6,
            "reply_text": "好的，已为您解析请求" + (f"：{intent}" if intent else "")}


def _forecast(ctx: dict[str, Any]) -> dict[str, Any]:
    seed = _seed(ctx)
    horizon = int(ctx.get("horizon_hours", 72))
    points = []
    for h in range(0, horizon, 6):
        base = 40 + (seed >> (h % 13)) % 30
        peak = 25 if (h % 24) in (12, 18) else 0  # 午晚高峰
        points.append({"offset_hours": h, "predicted_kg": round(base + peak, 1),
                       "lower": round(base * 0.7, 1), "upper": round((base + peak) * 1.3, 1)})
    return {"series": points, "model_desc": "mock-seasonal", "hot_level": (seed % 5) + 1}


def _pricing(ctx: dict[str, Any]) -> dict[str, Any]:
    seed = _seed(ctx)
    factor = round(0.9 + (seed % 21) / 100, 2)  # 0.90~1.10
    return {"price_factor": factor, "points_factor": round(2 - factor, 2),
            "reason": "mock：按片区近 7 日供需比推算", "valid_hours": 24}


# 品类 → 市场行情参考区间（元/公斤，mock 确定性采样）
_MARKET_PRICE_RANGES = {
    "纸类": (0.6, 1.4), "废纸": (0.6, 1.4), "废纸类": (0.6, 1.4), "纸箱": (0.8, 1.6),
    "塑料": (1.2, 2.6), "塑料类": (1.2, 2.6), "塑料瓶": (1.5, 3.0),
    "金属": (2.5, 5.0), "金属类": (2.5, 5.0), "易拉罐": (3.0, 6.0), "铝": (10.0, 16.0),
    "玻璃": (0.2, 0.6), "玻璃类": (0.2, 0.6), "酒瓶": (0.3, 0.8),
    "织物": (0.4, 1.2), "织物类": (0.4, 1.2), "旧衣服": (0.5, 1.4), "衣物": (0.5, 1.4),
    "家电": (4.0, 10.0), "家电类": (4.0, 10.0), "电子产品": (8.0, 20.0),
}


def _market_price(ctx: dict[str, Any]) -> dict[str, Any]:
    seed = _seed(ctx)
    category = str(ctx.get("category", ""))
    lo, hi = _MARKET_PRICE_RANGES.get(category, (0.5, 8.0))
    price = round(lo + (seed % 100) / 100 * (hi - lo), 2)
    return {"price_per_kg": price,
            "note": f"mock：{category} 市场区间参考 {lo}-{hi} 元/公斤（未配置搜索密钥）"}


def _agent_plan(ctx: dict[str, Any]) -> dict[str, Any]:
    return {"actions": [{
                "action_type": ctx.get("hint_action", "NOTIFY"),
                "target_uri": ctx.get("hint_uri", "/api/message/send"),
                "params": ctx.get("hint_params", {}),
                "risk_level": ctx.get("hint_risk", "LOW"),
                "reason": "mock 规划：按感知事件生成最小处置动作"}],
            "summary": "mock 规划完成"}


_GENERATORS = {
    "visual_verify": _visual_verify,
    "repair_diagnose": _repair_diagnose,
    "voice_parse": _voice_parse,
    "forecast": _forecast,
    "pricing": _pricing,
    "market_price": _market_price,
    "agent_plan": _agent_plan,
}

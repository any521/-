"""services.classify_service · CAM 云端精分类（/v1/classify/detail，契约 C6-2/3/5）

与 F1 verify 同能力（visual_verify）但出参冻结为「具体物品名 + 品类 + 纯净度/置信/建议」，
供端侧稳定目标裁剪图 → 精分类（自动链路与手动兜底共用）。

- 复用 verify_service（仅 import，不改其函数）：_rag_search/_build_prior/_correct_category/_VALID_CATEGORIES；
- 链路降级：llm_router.invoke 耗尽抛 AiRouteExhausted → main.py 全局转 3001（C6-5）；
- 入参校验失败（base64 超限/非法）抛 ValueError → router 转 400。
"""
from __future__ import annotations

import base64
import json
import os
import time

from pydantic import BaseModel, Field

from app.core import llm_router
from app.core.logging import get_logger
from app.schemas.classify import ClassifyDetailRequest, ClassifyDetailResponse
from app.services.verify_service import (  # 复用（禁止改动 verify_service 原函数）
    _build_prior,
    _correct_category,
    _rag_search,
)

log = get_logger("classify_service")

# 裁剪图上限（契约 C6-3）：base64 解码后 ≤300KB
MAX_CROP_BYTES = 300 * 1024

# 冻结 prompt（与 verify 区分：要求输出具体物品名；category 枚举与 C6-6 一致）
_PROMPT = (
    "你是垃圾分类专家。请识别图片中的物品，输出 JSON："
    '{"itemName":具体物品名,"category":品类,"purity":纯净度0-1,"confidence":置信度0-1,"advice":投放建议}。'
    "itemName 用常见中文名（如矿泉水瓶/易拉罐/瓦楞纸箱），≤20 字，不确定时输出最可能的名称；"
    "category 必须且只能输出以下之一：废纸类、塑料类、金属类、织物类、家电类、电子产品、玻璃类、厨余垃圾；"
    "advice 给出分类投放指引（含可回收时提示当日报价口径）。"
)

# 先验注入模板（与 verify_service 同语义，仅作先验，禁止照抄）
_PRIOR_HINT = "【参考数据（来源于真实回收数据集，仅作先验，禁止照抄）】\n{prior}"


class _ClassifyVision(BaseModel):
    """精分类内部出参（五字段，独立于 VisionOutput；itemName/advice 容空以便 mock/低质量输出通过）"""

    # 别名对齐 prompt 要求：LLM 输出 itemName（camelCase）与 item_name（snake_case）均可通过校验
    model_config = {"populate_by_name": True}

    item_name: str = Field("", alias="itemName", description="具体物品名，识别失败可空串")
    category: str = Field(..., description="品类（_correct_category 纠偏目标集合）")
    purity: float = Field(0.0, ge=0, le=1)
    confidence: float = Field(0.0, ge=0, le=1)
    advice: str = Field("", description="投放建议")


# 品类 → 元/kg 兜底表（与 front-shared RECYCLE_CATEGORIES 初始值对齐；厨余/未知 None）
_PRICE_FALLBACK = {
    "废纸类": 1.2, "塑料类": 1.6, "金属类": 3.8, "织物类": 0.8,
    "家电类": 6.5, "电子产品": 12.0, "玻璃类": 0.3, "厨余垃圾": None,
}

# real_base_prices.json 中可能的品类级价格键（实际数据为原材料吨价，无品类映射键时用兜底表）
_PRICE_JSON_KEYS = ("category_prices", "recycle_category_prices", "per_kg_prices")

_PRICE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
                           "data", "real_base_prices.json")


def _load_prices() -> dict[str, float | None]:
    """当日报价源：优先 data/real_base_prices.json 的品类级映射；缺失/解析失败用固定兜底表。"""
    try:
        with open(_PRICE_FILE, encoding="utf-8") as f:
            data = json.load(f)
        for key in _PRICE_JSON_KEYS:
            prices = data.get(key)
            if isinstance(prices, dict) and prices:
                return {k: (float(v) if v is not None else None)
                        for k, v in prices.items() if k in _PRICE_FALLBACK}
    except (OSError, ValueError, TypeError) as e:
        log.warning(f"品类价格加载失败，使用兜底表: {e}")
    return dict(_PRICE_FALLBACK)


_PRICES = _load_prices()


def _decode_crop(image_url: str) -> bytes:
    """base64 裁剪图解码（data URL 或纯 base64）；超限/非法抛 ValueError（router 转 400）"""
    data = image_url
    if data.startswith("data:"):
        comma = data.find(",")
        if comma < 0:
            raise ValueError("裁剪图 data URL 格式非法")
        data = data[comma + 1:]
    try:
        raw = base64.b64decode(data, validate=False)
    except (ValueError, TypeError) as e:
        raise ValueError(f"裁剪图 base64 解码失败: {e}") from e
    if not raw:
        raise ValueError("裁剪图为空")
    if len(raw) > MAX_CROP_BYTES:
        raise ValueError(f"裁剪图超过 {MAX_CROP_BYTES // 1024}KB 上限（契约 C6-3）")
    return raw


def classify_detail(req: ClassifyDetailRequest) -> ClassifyDetailResponse:
    # 1. 入参校验：base64 解码长度 ≤300KB（契约 C6-3），失败 400
    _decode_crop(req.image_url)

    # 2. RAG 先验（与 verify 同模式；失败/未就绪静默降级为空）
    prior = ""
    hits = _rag_search(req.category_hint or "可回收物", top_k=5, min_confidence=0.5)
    if hits:
        prior = _build_prior(hits)
    system_content = _PROMPT + (_PRIOR_HINT.format(prior=prior) if prior else "")

    # 3. 视觉 LLM（capability 复用 visual_verify；出参为精分类五字段；超时 30s 覆盖默认读超时）
    start = time.perf_counter()
    result = llm_router.invoke(
        capability="visual_verify",
        messages=[
            {"role": "system", "content": system_content},
            {"role": "user", "content": [
                {"type": "text", "text": f"端侧大类先验：{req.category_hint or '未提供'}"},
                {"type": "image_url", "image_url": {"url": req.image_url}},
            ]},
        ],
        response_model=_ClassifyVision,
        context=req.model_dump(),
        timeout_seconds=30,
    )
    vision: _ClassifyVision = result.parsed
    elapsed_ms = int((time.perf_counter() - start) * 1000)

    # 4. 品类纠偏（复用 verify_service；LLM 输出不在 8 类枚举时按数据集标准子类回填）
    _correct_category(vision)

    # 5. 当日报价：厨余/未知 → None（前端用 RECYCLE_CATEGORIES 兜底）
    price = _PRICES.get(vision.category)
    log.info(f"CAM classify category={vision.category} item={vision.item_name!r} "
             f"conf={vision.confidence:.2f} model={result.model} elapsed={elapsed_ms}ms")
    return ClassifyDetailResponse(
        item_name=vision.item_name,
        category=vision.category,
        confidence=vision.confidence,
        purity=vision.purity,
        advice=vision.advice,
        price_per_kg=price,
        degraded=result.degraded,
        elapsed_ms=elapsed_ms,
        model_name=f"{result.provider}/{result.model}",
    )

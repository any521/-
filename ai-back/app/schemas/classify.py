"""schemas.classify · CAM 精分类（/v1/classify/detail）出入参（契约 C6-2）"""
from __future__ import annotations

from pydantic import BaseModel, Field


class ClassifyDetailRequest(BaseModel):
    image_url: str = Field(..., description="裁剪目标区域 base64（≤300KB，契约 C6-3）")
    category_hint: str | None = Field(None, description="端侧大类 code 先验（paper/plastic/... 可空）")


class ClassifyDetailResponse(BaseModel):
    item_name: str = Field(..., description="具体物品名（≤20 字），识别失败可返回空串")
    category: str = Field(..., description="8 类中文枚举之一（契约 C6-6）")
    confidence: float = Field(..., ge=0, le=1)
    purity: float = Field(..., ge=0, le=1, description="纯净度 0-1")
    advice: str = Field(..., description="投放建议")
    price_per_kg: float | None = Field(None, description="当日报价 元/kg（厨余/未知为 None，前端用 RECYCLE_CATEGORIES 兜底）")
    degraded: bool = Field(False, description="链路降级标记")
    elapsed_ms: int = Field(0)
    model_name: str = Field("", description="provider/model，降级时为空")

"""schemas.forecast · F9 时空预测 + F5 动态定价出入参（Sprint 0 冻结）"""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


# ---------- F9 时空预测 ----------

class HistoryPoint(BaseModel):
    """历史观测点（Java 侧从 biz_recycle_order / biz_kitchen_report 回填）"""

    ts: datetime = Field(description="观测时间（ISO-8601）")
    kg: float = Field(ge=0, description="观测量（kg）")


class ForecastRequest(BaseModel):
    area_id: int = Field(description="片区 ID；楼栋级传 building_id")
    building_id: int | None = None
    target: Literal["RECYCLE", "KITCHEN"] = Field(description="预测对象：回收量 / 厨余量")
    horizon_hours: int = Field(default=72, ge=6, le=168, description="预测时长（默认 72h）")
    history: list[HistoryPoint] | None = Field(
        default=None, description="近 14 日历史序列（可选，缺省走确定性兜底序列）")


class ForecastPoint(BaseModel):
    offset_hours: int = Field(description="相对当前的小时偏移")
    predicted_kg: float
    lower: float = Field(description="置信下界")
    upper: float = Field(description="置信上界")


class ForecastResponse(BaseModel):
    series: list[ForecastPoint]
    model_desc: str = Field(description="prophet-vX / moving-average（降级兜底）")
    hot_level: int = Field(ge=1, le=5, description="片区热力等级 1-5，供大屏与派单")
    degraded: bool = False


# ---------- F5 动态定价 ----------

class PricingRequest(BaseModel):
    area_id: int
    category: str = Field(description="品类")
    base_price: float = Field(gt=0, description="基础报价（biz_recycle_price）")
    recent_supply_kg: float = Field(ge=0, description="近 7 日回收量")
    recent_demand_kg: float = Field(ge=0, description="近 7 日下游需求量")


class PricingResponse(BaseModel):
    price_factor: float = Field(gt=0, description="报价系数，最终价=base_price×factor（Java 侧夹逼上下限）")
    points_factor: float = Field(gt=0, description="积分系数")
    reason: str = Field(description="定价依据，供人工干预参考")
    valid_hours: int = Field(description="建议有效期（小时），过期回落基础价")
    degraded: bool = False

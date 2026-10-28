"""schemas.market_price · 回收市场行情联网报价（每日自动同步）出入参"""
from __future__ import annotations

from pydantic import BaseModel, Field


class MarketPriceRequest(BaseModel):
    category: str = Field(min_length=1, max_length=32, description="回收品类（biz_recycle_price.category）")
    city: str = Field(default="北京", min_length=1, max_length=32, description="城市（当地行情）")


class MarketPriceResponse(BaseModel):
    # LLM 通常不输出 category（prompt 未要求），调用方按请求品类回填，故允许空
    category: str | None = Field(default=None, description="品类（由调用方回填）")
    price_per_kg: float | None = Field(default=None, gt=0, description="当日回收价（元/公斤）；None=未检索到")
    # LLM 可能显式输出 null（无来源/无依据），Pydantic v2 显式 null 不触发 str 默认值，
    # 必须允许 None，否则整条提炼被校验丢弃（2026-08-12 修复：source:null 致每次校验失败）
    unit: str | None = Field(default=None, description="计价单位")
    note: str | None = Field(default=None, description="定价依据/来源说明")
    source: str | None = Field(default=None, description="命中来源（标题或搜索总结）")
    degraded: bool = Field(default=False, description="是否降级结果（正则提取/mock，非 LLM 提炼）")

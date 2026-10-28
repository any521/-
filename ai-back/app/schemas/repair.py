"""schemas.repair · F2 维修智能预诊出入参（Sprint 0 冻结，接 Track C 维修工单）"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class RepairDiagnoseRequest(BaseModel):
    order_id: int | None = Field(default=None, description="维修工单 ID（可空：下单前预诊）")
    device_type: str = Field(description="设备类型：冰箱/洗衣机/电视/小家电…")
    brand: str | None = None
    fault_desc: str = Field(min_length=2, description="用户故障描述")
    image_urls: list[str] = Field(default_factory=list, description="故障照片（可选）")


class FaultCandidate(BaseModel):
    fault: str = Field(description="可能故障")
    probability: float = Field(ge=0, le=1)


class RepairDiagnoseResponse(BaseModel):
    possible_faults: list[FaultCandidate] = Field(description="按概率降序")
    repairable_probability: float = Field(ge=0, le=1, description="可修复概率")
    suggested_parts: list[str] = Field(default_factory=list, description="建议备件")
    estimated_hours: float = Field(ge=0, description="预估工时（小时）")
    suggestion: Literal["ONSITE_REPAIR", "STATION_REPAIR", "REPLACE_OR_RECYCLE"] = Field(
        description="处置建议：上门修 / 站点修 / 建议换新并转回收（联动 D6）")
    reasoning: str = Field(description="诊断依据（RAG 命中知识摘要）")
    knowledge_refs: list[str] = Field(default_factory=list, description="命中知识库条目 ID")
    model_name: str
    degraded: bool

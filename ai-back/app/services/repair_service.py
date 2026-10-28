"""services.repair_service · F2 维修智能预诊（RAG + LLM，规则知识库兜底）

RAG：qdrant-client 可用则走向量检索（M2 接入）；否则用内置知识库关键词匹配，
保证 AI 链路耗尽时仍能按 KNOWLEDGE_RULE 兜底出诊断建议（降级不阻塞 Track C 工单）。
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

from app.core import llm_router
from app.core.llm_router import AiRouteExhausted
from app.core.logging import get_logger
from app.schemas.repair import FaultCandidate, RepairDiagnoseRequest, RepairDiagnoseResponse

log = get_logger("repair_service")


class _LlmDiagnose(BaseModel):
    """LLM 出参子集（knowledge_refs/model_name/degraded 由 service 补充）"""

    possible_faults: list[FaultCandidate]
    repairable_probability: float = Field(ge=0, le=1)
    suggested_parts: list[str] = []
    estimated_hours: float = Field(ge=0)
    suggestion: Literal["ONSITE_REPAIR", "STATION_REPAIR", "REPLACE_OR_RECYCLE"]
    reasoning: str

# 内置知识库（占位数据集；M2 迁移 Qdrant，条目 ID 即 knowledge_refs）
_KNOWLEDGE = [
    {"id": "KB-001", "keywords": ["不制冷", "冰箱"], "fault": "制冷剂泄漏/压缩机故障",
     "parts": ["压缩机", "制冷剂"], "hours": 2.5, "repairable": 0.7},
    {"id": "KB-002", "keywords": ["不脱水", "洗衣机"], "fault": "排水泵堵塞/电机皮带松脱",
     "parts": ["排水泵", "皮带"], "hours": 1.0, "repairable": 0.9},
    {"id": "KB-003", "keywords": ["黑屏", "电视"], "fault": "背光板/电源板故障",
     "parts": ["背光条", "电源板"], "hours": 1.5, "repairable": 0.8},
    {"id": "KB-004", "keywords": ["烧", "焦味", "冒烟"], "fault": "电路短路元件烧毁",
     "parts": ["主板"], "hours": 3.0, "repairable": 0.25},
    {"id": "KB-005", "keywords": ["不通电", "没反应"], "fault": "电源线/保险丝断路",
     "parts": ["电源线", "保险丝"], "hours": 0.5, "repairable": 0.95},
]

_PROMPT = (
    "你是家电维修专家。根据设备类型、故障描述与参考知识，输出 JSON 诊断："
    '{"possible_faults":[{"fault":..,"probability":..}],"repairable_probability":..,'
    '"suggested_parts":[..],"estimated_hours":..,'
    '"suggestion":"ONSITE_REPAIR|STATION_REPAIR|REPLACE_OR_RECYCLE","reasoning":..}'
)


def _retrieve(req: RepairDiagnoseRequest) -> list[dict]:
    """检索知识库（当前关键词匹配；M2 切 Qdrant 向量检索，接口不变）"""
    text = f"{req.device_type} {req.fault_desc}"
    return [kb for kb in _KNOWLEDGE if any(k in text for k in kb["keywords"])]


def _rule_fallback(req: RepairDiagnoseRequest, hits: list[dict]) -> RepairDiagnoseResponse:
    """KNOWLEDGE_RULE 兜底：直接由知识库命中拼装诊断"""
    if not hits:
        return RepairDiagnoseResponse(
            possible_faults=[{"fault": "待上门检测", "probability": 0.5}],
            repairable_probability=0.5, suggested_parts=[], estimated_hours=1.0,
            suggestion="ONSITE_REPAIR", reasoning="知识库未命中，建议技术员上门检测",
            knowledge_refs=[], model_name="rule/knowledge-fallback", degraded=True)
    top = hits[0]
    suggestion = "REPLACE_OR_RECYCLE" if top["repairable"] < 0.4 else "ONSITE_REPAIR"
    return RepairDiagnoseResponse(
        possible_faults=[{"fault": h["fault"], "probability": h["repairable"]} for h in hits[:3]],
        repairable_probability=top["repairable"],
        suggested_parts=top["parts"], estimated_hours=top["hours"],
        suggestion=suggestion,
        reasoning=f"规则兜底：命中知识库 {top['id']}（{top['fault']}）",
        knowledge_refs=[h["id"] for h in hits[:3]],
        model_name="rule/knowledge-fallback", degraded=True)


def diagnose(req: RepairDiagnoseRequest) -> RepairDiagnoseResponse:
    hits = _retrieve(req)
    knowledge_text = "\n".join(f"[{h['id']}] {h['fault']} 备件:{h['parts']}" for h in hits) or "无"
    try:
        result = llm_router.invoke(
            capability="repair_diagnose",
            messages=[
                {"role": "system", "content": _PROMPT},
                {"role": "user", "content": f"设备:{req.device_type} 品牌:{req.brand or '未知'}\n"
                                            f"故障描述:{req.fault_desc}\n参考知识:\n{knowledge_text}"},
            ],
            response_model=_LlmDiagnose,
            context=req.model_dump(),
        )
        parsed: _LlmDiagnose = result.parsed
        return RepairDiagnoseResponse(
            **parsed.model_dump(),
            knowledge_refs=[h["id"] for h in hits[:3]],
            model_name=f"{result.provider}/{result.model}",
            degraded=result.degraded)
    except AiRouteExhausted:
        log.warning("F2 路由链耗尽，走 KNOWLEDGE_RULE 兜底")
        return _rule_fallback(req, hits)

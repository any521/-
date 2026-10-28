"""schemas.agent · F8 多智能体决策出入参（与 dianfei-common AgentDecision / biz_agent_decision 对齐）"""
from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

AgentType = Literal["AGENT-01", "AGENT-02", "AGENT-03", "AGENT-04"]
RiskLevel = Literal["LOW", "MEDIUM", "HIGH"]


class AgentEvent(BaseModel):
    """感知输入：一条待处理事件（工单/告警/咨询/指标）"""

    event_type: str = Field(description="PENDING_ORDER / RISK_ALERT / USER_TICKET / DAILY_METRICS")
    payload: dict[str, Any] = Field(default_factory=dict)


class AgentRunRequest(BaseModel):
    agent_type: AgentType
    events: list[AgentEvent] = Field(min_length=1)
    dry_run: bool = Field(default=False, description="true=只产出决策不回调平台接口")


class PlannedAction(BaseModel):
    """LLM 规划出的单个动作（结构化强校验）"""

    action_type: str = Field(description="REDISPATCH / FREEZE_POINTS / REPLY_TICKET / NOTIFY …")
    target_uri: str = Field(description="平台白名单 REST（审批通过后框架代调）")
    params: dict[str, Any] = Field(default_factory=dict)
    risk_level: RiskLevel
    reason: str = Field(description="依据摘要，进决策单依据链")


class AgentPlanOutput(BaseModel):
    actions: list[PlannedAction] = Field(default_factory=list)
    summary: str = ""


class DecisionDraft(BaseModel):
    """回传 Java 侧落库 biz_agent_decision 的决策单草稿"""

    agent_type: AgentType
    action: str
    target_uri: str
    payload_json: dict[str, Any]
    risk_level: RiskLevel
    evidences: list[dict[str, str]] = Field(
        default_factory=list, description="[{source:RULE|MODEL|DATA_QUERY, description, data_ref}]")
    executed: bool = Field(description="LOW 且非 dry_run 时已由 act 节点直接执行")
    execute_result: str | None = None


class AgentRunResponse(BaseModel):
    agent_type: AgentType
    decisions: list[DecisionDraft]
    summary: str
    model_name: str
    degraded: bool

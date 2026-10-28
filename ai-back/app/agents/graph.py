"""agents.graph · F8 编排骨架：perceive → plan → act/guard → audit（06 章）

优先使用 LangGraph（requirements 可选依赖）；未安装时用同构的内置顺序图引擎，
节点语义与 06 章伪代码一一对应，后续切换 LangGraph 不改节点函数签名。
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable

from app.core.logging import get_logger

log = get_logger("agent_graph")


@dataclass
class AgentState:
    """图状态：贯穿 perceive→plan→act/guard→audit"""

    agent_type: str
    events: list[dict[str, Any]] = field(default_factory=list)
    planned_actions: list[dict[str, Any]] = field(default_factory=list)
    decisions: list[dict[str, Any]] = field(default_factory=list)  # DecisionDraft dict
    summary: str = ""
    model_name: str = ""
    degraded: bool = False
    dry_run: bool = False


Node = Callable[[AgentState], AgentState]


def run_graph(state: AgentState, perceive: Node, plan: Node,
              act: Node, guard: Node, audit: Node) -> AgentState:
    """perceive → plan → (逐动作: LOW→act / MID·HIGH→guard) → audit"""
    state = perceive(state)
    state = plan(state)
    low, gated = [], []
    for action in state.planned_actions:
        (low if action.get("risk_level") == "LOW" else gated).append(action)
    if low:
        sub = AgentState(**{**state.__dict__, "planned_actions": low})
        state.decisions += act(sub).decisions
    if gated:
        sub = AgentState(**{**state.__dict__, "planned_actions": gated})
        state.decisions += guard(sub).decisions
    return audit(state)

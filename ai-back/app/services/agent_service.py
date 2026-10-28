"""services.agent_service · F8 多智能体运行（06 章：perceive→plan→act/guard→audit）

四类 Agent（调度 AGENT-01 / 风控 AGENT-02 / 客服 AGENT-03 / 复盘 AGENT-04）共用同一图骨架，
差异只在 plan 节点的职责 prompt。plan 走 llm_router 能力 agent_plan（deepseek 链 + mock 兜底），
路由链耗尽时降级 RULE_ENGINE 规则规划（degraded=True），Agent 中枢不因 AI 宕机停摆。

执行边界（安全，对齐 06 章"行动可控"）：本服务不持平台凭据、不回调平台接口；
act/guard 节点只做风险分流并产出决策单草稿（DecisionDraft），统一由 Java 侧
dianfei-ai 决策单执行框架落库 biz_agent_decision（哈希入链）后按风险等级执行：
LOW 自动执行 / MEDIUM 运营审批 / HIGH 双人复核（HITL）。
"""
from __future__ import annotations

import json
from typing import Any

from app.agents.graph import AgentState, run_graph
from app.core.llm_router import AiRouteExhausted, invoke
from app.core.logging import get_logger
from app.schemas.agent import (AgentPlanOutput, AgentRunRequest,
                               AgentRunResponse, DecisionDraft)

log = get_logger("agent_service")

# ==================== RAG 检索接入（RAG-03 F8 可选，契约 C2；RAG-02 未交付/失败一律静默降级） ====================
try:
    from app.rag import retriever
    _RAG_OK = True
except Exception:          # 模块缺失（RAG-02 未交付）或依赖缺失
    retriever = None
    _RAG_OK = False

# ==================== 联网搜索接入（可选知识源；未配置 key/失败一律静默降级） ====================
try:
    from app.services import web_search
    _WEB_OK = True
except Exception:          # 依赖缺失或异常
    web_search = None
    _WEB_OK = False


def _common_items_snippet(events: list[dict]) -> str:
    """客服参考片段：从 USER_TICKET 事件 payload 提取品类 → category_prior top_names
    （RAG-04 未交付前 category_prior 恒 None，自然降级不追加；不做不阻塞交付）"""
    if not _RAG_OK:
        return ""
    try:
        for e in events:
            if e.get("event_type") != "USER_TICKET":
                continue
            payload = e.get("payload") or {}
            category = payload.get("category") or payload.get("品类")
            if not category:
                continue
            prior = retriever.category_prior(category)
            if prior and prior.get("top_names"):
                log.info(f"F8 客服先验命中 category={category}")
                return f"\n【平台常见回收品参考】{category}: {'、'.join(prior['top_names'][:8])}"
    except Exception as e:
        log.warning(f"RAG 客服先验降级: {e}")
    return ""


def _extract_query(events: list[dict]) -> str:
    """从 USER_TICKET 事件 payload 提取咨询文本作为搜索 query（多种键名兜底）"""
    for e in events:
        if e.get("event_type") != "USER_TICKET":
            continue
        payload = e.get("payload") or {}
        for key in ("content", "text", "question", "message", "咨询内容", "问题"):
            val = payload.get(key)
            if isinstance(val, str) and val.strip():
                return val.strip()
        strings = [str(v) for v in payload.values() if isinstance(v, str) and v.strip()]
        return "，".join(strings)[:200] if strings else ""
    return ""


def _web_snippet(events: list[dict]) -> str:
    """联网搜索片段：客服 Agent 收到真实提问时先搜后答（未配置 key/失败自然降级）"""
    if not _WEB_OK:
        return ""
    query = _extract_query(events)
    if not query:
        return ""
    try:
        hits = web_search.search(query)
        if hits:
            log.info(f"F8 客服联网搜索命中 query={query[:40]}")
        return web_search.to_prompt_snippet(hits)
    except Exception as e:
        log.warning(f"F8 客服联网搜索降级: {e}")
        return ""

# 各 Agent 职责 prompt（06 章 6.1 职责分工）
_ROLE_PROMPTS = {
    "AGENT-01": "你是调度 Agent：针对待派工单结合预测生成派单/重排动作（REDISPATCH），一般为 LOW 风险。",
    "AGENT-02": "你是风控 Agent：针对异常告警生成冻结积分/处置动作（FREEZE_POINTS），冻结类为 HIGH 风险。",
    "AGENT-03": "你是客服 Agent：针对用户咨询生成回复/改期动作（REPLY_TICKET / RESCHEDULE），回复为 LOW，改期为 MEDIUM。",
    "AGENT-04": "你是复盘 Agent：针对经营指标生成日报摘要与优化建议通知（NOTIFY），为 LOW 风险。",
}

_PROMPT = (
    "根据待处理事件规划最小处置动作序列，输出 JSON："
    '{"actions":[{"action_type":"REDISPATCH|FREEZE_POINTS|REPLY_TICKET|NOTIFY|...",'
    '"target_uri":"/api/...","params":{},"risk_level":"LOW|MEDIUM|HIGH","reason":".."}],'
    '"summary":".."}。'
    "只能使用平台白名单 REST（/api/dispatch/plan、/api/carbon/points/freeze、"
    "/api/message/send 等），退款/冻结/大额积分调整必须 HIGH。"
)

# RULE_ENGINE 兜底：事件类型 → 确定性最小动作（AI 不可用时中枢仍可运转）
_RULE_ACTIONS: dict[str, dict[str, Any]] = {
    "PENDING_ORDER": {"action_type": "REDISPATCH", "target_uri": "/api/dispatch/plan",
                      "risk_level": "LOW", "reason": "规则：待派工单触发重排派单"},
    "RISK_ALERT": {"action_type": "FREEZE_POINTS", "target_uri": "/api/carbon/points/freeze",
                   "risk_level": "HIGH", "reason": "规则：风控告警冻结可疑积分，须双人复核"},
    "USER_TICKET": {"action_type": "REPLY_TICKET", "target_uri": "/api/message/send",
                    "risk_level": "LOW", "reason": "规则：客服工单生成标准回复"},
    "DAILY_METRICS": {"action_type": "NOTIFY", "target_uri": "/api/message/send",
                      "risk_level": "LOW", "reason": "规则：经营指标聚合生成日报通知"},
}


def _perceive(state: AgentState) -> AgentState:
    """感知：事件标准化（已由 Pydantic 校验，这里聚合摘要供 plan 上下文）"""
    log.info(f"{state.agent_type} perceive events={len(state.events)}")
    return state


def _plan(state: AgentState) -> AgentState:
    """规划：LLM 结构化输出动作序列；路由耗尽降级规则引擎"""
    # RAG-03 F8 可选：客服 Agent 追加平台常见回收品参考（category_prior 未就绪时自然降级）
    snippet = _common_items_snippet(state.events) if state.agent_type == "AGENT-03" else ""
    if state.agent_type == "AGENT-03":
        snippet += _web_snippet(state.events)
    messages = [
        {"role": "system", "content": _ROLE_PROMPTS[state.agent_type] + _PROMPT},
        {"role": "user", "content": json.dumps(state.events, ensure_ascii=False) + snippet},
    ]
    try:
        result = invoke("agent_plan", messages, AgentPlanOutput,
                        context={"agent_type": state.agent_type, "events": repr(state.events)})
        plan: AgentPlanOutput = result.parsed
        state.planned_actions = [a.model_dump() for a in plan.actions]
        state.summary = plan.summary
        state.model_name = f"{result.provider}/{result.model}"
        state.degraded = result.degraded
    except AiRouteExhausted:
        state.planned_actions = [
            {**_RULE_ACTIONS[e["event_type"]], "params": e.get("payload", {})}
            for e in state.events if e.get("event_type") in _RULE_ACTIONS
        ]
        state.summary = f"RULE_ENGINE 兜底规划 {len(state.planned_actions)} 个动作"
        state.model_name = "rule/agent-fallback"
        state.degraded = True
    return state


def _draft(state: AgentState, action: dict[str, Any], source: str) -> dict[str, Any]:
    """动作 → 决策单草稿（Java 侧落库 biz_agent_decision 并执行）"""
    return DecisionDraft(
        agent_type=state.agent_type,
        action=action["action_type"],
        target_uri=action["target_uri"],
        payload_json=action.get("params", {}),
        risk_level=action["risk_level"],
        evidences=[{"source": source, "description": action.get("reason", ""),
                    "data_ref": f"events:{len(state.events)}"}],
        executed=False,
        execute_result=None,
    ).model_dump()


def _act(state: AgentState) -> AgentState:
    """行动分流（LOW）：产出可自动执行的决策草稿，由 Java 决策框架白名单代调并回写"""
    source = "RULE" if state.degraded else "MODEL"
    state.decisions = [_draft(state, a, source) for a in state.planned_actions]
    return state


def _guard(state: AgentState) -> AgentState:
    """风险门（MEDIUM/HIGH）：决策草稿进入人工审批队列（HITL）"""
    source = "RULE" if state.degraded else "MODEL"
    state.decisions = [_draft(state, a, source) for a in state.planned_actions]
    return state


def _audit(state: AgentState) -> AgentState:
    """审计：结构化日志留痕（决策哈希入链在 Java 落库侧完成）"""
    log.info(f"{state.agent_type} audit decisions={len(state.decisions)} "
             f"degraded={state.degraded} dry_run={state.dry_run}")
    return state


def run(req: AgentRunRequest) -> AgentRunResponse:
    state = AgentState(
        agent_type=req.agent_type,
        events=[e.model_dump() for e in req.events],
        dry_run=req.dry_run,
    )
    state = run_graph(state, _perceive, _plan, _act, _guard, _audit)
    return AgentRunResponse(
        agent_type=req.agent_type,
        decisions=[DecisionDraft.model_validate(d) for d in state.decisions],
        summary=state.summary,
        model_name=state.model_name,
        degraded=state.degraded,
    )

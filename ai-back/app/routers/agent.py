"""routers.agent · F8 多智能体运行入口（POST /v1/agent/run）"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.agent import AgentRunRequest, AgentRunResponse
from app.schemas.common import Result
from app.services import agent_service

router = APIRouter(prefix="/v1/agent", tags=["F8 多智能体"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/run", response_model=Result[AgentRunResponse])
def run(req: AgentRunRequest) -> Result[AgentRunResponse]:
    """触发一次 Agent 运行（perceive→plan→act/guard→audit），返回决策单草稿"""
    return Result.ok(agent_service.run(req), trace_id_var.get())

"""routers.repair · F2 维修智能预诊（Java /api/ai/repair/diagnose → 本服务 /v1/repair/diagnose）"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.common import Result
from app.schemas.repair import RepairDiagnoseRequest, RepairDiagnoseResponse
from app.services import repair_service

router = APIRouter(prefix="/v1/repair", tags=["F2 维修预诊"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/diagnose", response_model=Result[RepairDiagnoseResponse])
def diagnose(req: RepairDiagnoseRequest) -> Result[RepairDiagnoseResponse]:
    return Result.ok(repair_service.diagnose(req), trace_id_var.get())

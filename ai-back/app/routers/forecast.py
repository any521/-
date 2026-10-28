"""routers.forecast · F9 时空预测 + F5 动态定价

Java /api/ai/forecast/building → 本服务 /v1/forecast；定价 /v1/pricing。
"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.common import Result
from app.schemas.forecast import (ForecastRequest, ForecastResponse,
                                  PricingRequest, PricingResponse)
from app.services import forecast_service

router = APIRouter(prefix="/v1", tags=["F9 预测 / F5 定价"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/forecast", response_model=Result[ForecastResponse])
def forecast(req: ForecastRequest) -> Result[ForecastResponse]:
    return Result.ok(forecast_service.forecast(req), trace_id_var.get())


@router.post("/pricing", response_model=Result[PricingResponse])
def pricing(req: PricingRequest) -> Result[PricingResponse]:
    return Result.ok(forecast_service.pricing(req), trace_id_var.get())

"""routers.market_price · 回收市场行情（联网报价，每日自动同步用）

Java 每日定时任务 → 本服务 /v1/market-price（博查/Tavily 搜索 + LLM 提炼当日报价）。
"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.common import Result
from app.schemas.market_price import MarketPriceRequest, MarketPriceResponse
from app.services import market_price_service

router = APIRouter(prefix="/v1", tags=["回收市场行情"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/market-price", response_model=Result[MarketPriceResponse])
def market_price(req: MarketPriceRequest) -> Result[MarketPriceResponse]:
    return Result.ok(market_price_service.fetch_market_price(req), trace_id_var.get())

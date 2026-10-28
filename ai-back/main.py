"""dianfei-ai-service · Python AI 旁路服务入口（05 章 / Track E E0）

- 仅接受内网调用：X-Internal-Token（core/security，未配置 AI_INTERNAL_TOKEN 时为开发模式）
- 统一响应壳 Result（HTTP 恒 200，code 承载语义；3001=AI 不可用触发降级）
- 请求追踪：X-Trace-Id 透传（Java 侧生成）或自动生成，贯穿结构化日志
- 启动：uvicorn main:app --host 0.0.0.0 --port 8100
"""
from __future__ import annotations

import os
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.config import validate_production_config
from app.core.llm_router import AiRouteExhausted
from app.core.logging import get_logger, new_trace_id, setup_logging, trace_id_var
from app.routers import (agent, classify, forecast, market_price, repair,
                         verify, voice, yolo)
from app.schemas.common import (CODE_AI_UNAVAILABLE, CODE_PARAM_ERROR,
                                CODE_SYSTEM_ERROR, Result)

# 生产环境配置 fail-closed 校验（AI_ENV=prod 时 AI_INTERNAL_TOKEN 必须配置且 >= 32 字符）
validate_production_config()

setup_logging()
log = get_logger("main")

AI_ENV = os.environ.get("AI_ENV", "dev")
IS_PROD = AI_ENV == "prod"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """RAG 索引预热（RAG-02；无索引/失败仅 warn，不阻断启动）"""
    from app.rag import loader
    loader.warmup()
    yield


app = FastAPI(
    title="点废成金 AI 服务",
    version="2.0.0",
    description="点废成金 AI 旁路服务：多供应商路由 + F1/F2/F7/F9/F5 能力 + F8 多智能体",
    docs_url=None if IS_PROD else "/docs",
    redoc_url=None if IS_PROD else "/redoc",
    openapi_url=None if IS_PROD else "/openapi.json",
    lifespan=lifespan,
)

app.include_router(verify.router)
app.include_router(classify.router)
app.include_router(repair.router)
app.include_router(voice.router)
app.include_router(forecast.router)
app.include_router(agent.router)
app.include_router(yolo.router)
app.include_router(market_price.router)


@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    """追踪 ID 贯穿 + 访问日志（Java 侧经 X-Trace-Id 透传）"""
    trace_id = request.headers.get("X-Trace-Id") or new_trace_id()
    trace_id_var.set(trace_id)
    start = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = int((time.perf_counter() - start) * 1000)
    log.info(f"{request.method} {request.url.path} -> {response.status_code} {elapsed_ms}ms")
    response.headers["X-Trace-Id"] = trace_id
    return response


@app.exception_handler(AiRouteExhausted)
async def route_exhausted_handler(_: Request, exc: AiRouteExhausted):
    """路由链耗尽 → 3001，Java 侧据 fallback 代号走降级（转人工/兜底）"""
    return JSONResponse(status_code=200, content=Result.fail(
        CODE_AI_UNAVAILABLE, f"AI 路由链耗尽 fallback={exc.fallback}",
        trace_id_var.get()).model_dump())


@app.exception_handler(RequestValidationError)
async def validation_handler(_: Request, exc: RequestValidationError):
    return JSONResponse(status_code=200, content=Result.fail(
        CODE_PARAM_ERROR, f"参数校验失败: {exc.errors()[:3]}", trace_id_var.get()).model_dump())


@app.exception_handler(Exception)
async def unexpected_handler(_: Request, exc: Exception):
    log.exception("未处理异常")
    return JSONResponse(status_code=200, content=Result.fail(
        CODE_SYSTEM_ERROR, f"{type(exc).__name__}: {exc}", trace_id_var.get()).model_dump())


@app.get("/healthz", tags=["探针"])
async def healthz():
    """存活探针：进程存活即 200"""
    return {"status": "UP"}


@app.get("/readyz", tags=["探针"])
async def readyz():
    """就绪探针：配置可加载 + 路由表完整即就绪"""
    from app.core.config import get_providers, _routing_json
    providers = [p["name"] for p in get_providers()]
    routes = list(_routing_json.data.get("routes", {}).keys())
    ready = bool(providers) and bool(routes)
    return JSONResponse(status_code=200 if ready else 503, content={
        "status": "READY" if ready else "NOT_READY",
        "providers": providers, "routes": routes})

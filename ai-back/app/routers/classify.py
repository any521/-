"""routers.classify · CAM 云端精分类（契约 C6-2）：Java 编排层 /api/ai/classify → 本服务 /v1/classify/detail"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.classify import ClassifyDetailRequest, ClassifyDetailResponse
from app.schemas.common import CODE_PARAM_ERROR, Result
from app.services import classify_service

router = APIRouter(prefix="/v1/classify", tags=["CAM 精分类"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/detail", response_model=Result[ClassifyDetailResponse])
def classify_detail(req: ClassifyDetailRequest) -> Result[ClassifyDetailResponse]:
    """裁剪目标区域 base64 → 视觉 LLM 精分类（具体物品名 + 8 类枚举 + 当日报价）。

    入参校验失败（base64 超 300KB/非法，契约 C6-3）→ 400；AI 路由链耗尽
    （AiRouteExhausted）由 main.py 全局 handler 转 3001（契约 C6-5）。
    """
    try:
        data = classify_service.classify_detail(req)
    except ValueError as e:
        # 裁剪图规格违规：参数错误，Java 侧同样先校验（双层防线）
        return Result.fail(CODE_PARAM_ERROR, str(e), trace_id_var.get())
    return Result.ok(data, trace_id_var.get())

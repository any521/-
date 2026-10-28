"""routers.verify · F1 视觉审核（Java 编排层 /api/ai/verify/image → 本服务 /v1/verify/image）"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.common import Result
from app.schemas.verify import (Build3dRequest, Build3dResponse,
                                VerifyImageRequest, VerifyImageResponse)
from app.services import build3d_service, verify_service

router = APIRouter(prefix="/v1/verify", tags=["F1 视觉审核"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/image", response_model=Result[VerifyImageResponse])
def verify_image(
    req: VerifyImageRequest,
    pass_threshold: float = Query(default=verify_service.PASS_THRESHOLD, ge=0, le=1,
                                  description="Java 侧 sys_config ai.verify.pass_threshold 透传"),
    review_threshold: float = Query(default=verify_service.REVIEW_THRESHOLD, ge=0, le=1),
) -> Result[VerifyImageResponse]:
    data = verify_service.verify_image(req, pass_threshold, review_threshold)
    return Result.ok(data, trace_id_var.get())


@router.post("/3d-build", response_model=Result[Build3dResponse])
def build_3d(
    req: Build3dRequest,
) -> Result[Build3dResponse]:
    """3D 模型构建（img2threejs 风格）：视觉 LLM 看真实图片 → spec JSON，前端执行器渲染。
    mode=standard（默认，识别链路）/advanced（spec 多轮自纠）/extreme（渲染截图对比闭环，
    Playwright 不可用时降级 advanced）。spec=None 表示构建失败，前端回退预设模型库（永不阻断）。"""
    data = build3d_service.build3d(req.image_url, mode=req.mode)
    return Result.ok(data, trace_id_var.get())

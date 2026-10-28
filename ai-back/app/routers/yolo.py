"""routers.yolo · F6/YOLO 图片检测路由（V44 F7 升级新增）
- POST /v1/yolo/detect
- 内部调用（X-Internal-Token 鉴权），Java 侧代理转发
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.common import CODE_PARAM_ERROR, Result
from app.schemas.yolo import YoloDetectRequest, YoloDetectResponse
from app.services import yolo_service

router = APIRouter(prefix="/v1/yolo", tags=["F6 YOLO 检测"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/detect", response_model=Result[YoloDetectResponse])
def detect(
    req: YoloDetectRequest,
    review_threshold: float = Query(default=0.35, ge=0, le=1, description="置信度阈值"),
) -> Result[YoloDetectResponse]:
    """图片 base64 → ONNX YOLO 检测（标注图 + 原图坐标框）。

    入参校验失败（base64 超限/图片不可解码）→ 400；模型缺失/推理异常
    （AiRouteExhausted）由 main.py 全局 handler 转 3001。
    """
    try:
        data = yolo_service.detect_image(req)
    except ValueError as e:
        # 图片规格违规：参数错误，Java 侧同样先校验（双层防线）
        return Result.fail(CODE_PARAM_ERROR, str(e), trace_id_var.get())
    return Result.ok(data, trace_id_var.get())

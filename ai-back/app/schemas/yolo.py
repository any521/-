"""schemas.yolo · F6/YOLO 图片检测（/v1/yolo/detect，V44 F7 升级新增）

字段与 Java 侧 AiContracts.YoloDetectRequest/Response 对齐（snake_case，@JsonNaming 序列化一致）。
"""
from __future__ import annotations

from pydantic import Field
from pydantic import BaseModel


class YoloDetectRequest(BaseModel):
    """图片检测入参（image_base64 与端侧上传一致：data URL 或纯 base64，≤5MB）"""

    image_base64: str = Field(..., description="图片 base64（≤5MB）")
    score_threshold: float | None = Field(None, ge=0, le=1, description="置信度阈值，缺省用模型默认 0.35")


class YoloDetectionItem(BaseModel):
    """单目标检测结果（bbox 为原图像素 [x1,y1,x2,y2]，center 为框中心 [cx,cy]）"""

    class_name: str = Field(..., description="类别（glass/metal/paper/plastic/kitchen/other）")
    confidence: float = Field(..., ge=0, le=1)
    bbox: list[int] = Field(..., min_length=4, max_length=4, description="原图坐标 [x1,y1,x2,y2]")
    center: list[float] = Field(..., min_length=2, max_length=2, description="框中心 [cx,cy]")


class YoloDetectResponse(BaseModel):
    """检测结果：目标列表 + 原图尺寸 + 标注图（JPEG base64）"""

    detections: list[YoloDetectionItem] = Field(default_factory=list)
    image_width: int
    image_height: int
    annotated_image_base64: str | None = Field(None, description="绘制检测框后的 JPEG base64")
    model_name: str = Field("", description="实际推理模型名")
    degraded: bool = Field(False, description="链路降级标记（当前真实 ONNX 推理恒 False）")

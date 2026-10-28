"""services.yolo_service · F6/YOLO 图片检测（/v1/yolo/detect，V44 F7 升级新增）

端侧（front-user onnxruntime-web）同款模型的云端兜底推理：
- letterbox 方形预处理（与 yoloDecode.ts sourceToTensor 一致，填充 #727272=114）
- ONNX 推理（onnxruntime，CPU）→ 解码 YOLOv8 系输出 [1, 4+C, N]（cx,cy,w,h 为 letterbox 像素坐标）
- IoU 非极大值抑制 + 包含抑制（与 yoloDecode.ts nms 同逻辑，maxKeep=4）
- 类别与 /models/manifest.json 冻结一致：glass/metal/paper/plastic/kitchen/other
- 模型文件缺失/加载失败/推理异常 → 抛 AiRouteExhausted → main.py 全局转 3001（Java 侧降级）
- 入参非法（base64 超限/解码失败）抛 ValueError → router 转 400
"""
from __future__ import annotations

import base64
import os
import threading

import numpy as np

from app.core.llm_router import AiRouteExhausted
from app.core.logging import get_logger
from app.schemas.yolo import YoloDetectRequest, YoloDetectResponse, YoloDetectionItem

log = get_logger("yolo_service")

# ---- 模型资产（与 front-user /models/manifest.json 对齐，发版即换） ----
_MODEL_NAME = "yolo-recycle-v0.3.1"
_INPUT_SIZE = 640
_DEFAULT_SCORE = 0.35
_IOU_THRESHOLD = 0.45
_MAX_KEEP = 4
_LABELS = ["glass", "metal", "paper", "plastic", "kitchen", "other"]

# 上传图片上限（契约 5MB）
MAX_IMAGE_BYTES = 5 * 1024 * 1024


def _find_model() -> str | None:
    """模型查找顺序：环境变量 YOLO_MODEL_PATH → ai-back/models/ → front-user/public/models/"""
    root = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
    candidates = [
        os.environ.get("YOLO_MODEL_PATH", ""),
        os.path.join(root, "models", f"{_MODEL_NAME}.onnx"),
        os.path.join(root, "..", "front-user", "public", "models", f"{_MODEL_NAME}.onnx"),
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    return None


_session = None
_session_lock = threading.Lock()


def _load_session():
    """懒加载 ONNX Session（线程安全）；模型缺失/加载失败 → 3001 降级"""
    global _session
    if _session is not None:
        return _session
    with _session_lock:
        if _session is not None:
            return _session
        path = _find_model()
        if path is None:
            log.warning("YOLO 模型未找到（可设置 YOLO_MODEL_PATH 或放置 %s.onnx 到 ai-back/models/）", _MODEL_NAME)
            raise AiRouteExhausted("yolo_detect", "MANUAL")
        try:
            import onnxruntime as ort
            _session = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
            log.info("YOLO 模型加载成功: %s", path)
        except Exception as e:  # noqa: BLE001
            log.warning("YOLO 模型加载失败: %s", e)
            raise AiRouteExhausted("yolo_detect", "MANUAL") from e
    return _session


def _decode_base64(image_base64: str) -> bytes:
    """data URL 或纯 base64 → 原始字节；非法/超限抛 ValueError（router 转 400）"""
    data = image_base64
    if data.startswith("data:"):
        comma = data.find(",")
        if comma < 0:
            raise ValueError("图片 data URL 格式非法")
        data = data[comma + 1:]
    try:
        raw = base64.b64decode(data, validate=False)
    except (ValueError, TypeError) as e:
        raise ValueError(f"图片 base64 解码失败: {e}") from e
    if not raw:
        raise ValueError("图片为空")
    if len(raw) > MAX_IMAGE_BYTES:
        raise ValueError(f"图片超过 {MAX_IMAGE_BYTES // (1024 * 1024)}MB 上限")
    return raw


def _preprocess(img_bgr: np.ndarray, size: int) -> tuple[np.ndarray, float, int, int]:
    """letterbox 方形预处理（与端侧 sourceToTensor 同语义）→ [1,3,size,size] Float32 0-1"""
    import cv2

    h, w = img_bgr.shape[:2]
    scale = min(size / w, size / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    resized = cv2.resize(img_bgr, (nw, nh), interpolation=cv2.INTER_LINEAR)
    dw, dh = (size - nw) // 2, (size - nh) // 2
    canvas[dh:dh + nh, dw:dw + nw] = resized
    rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    tensor = rgb.transpose(2, 0, 1)[None]
    return np.ascontiguousarray(tensor), scale, dw, dh


def _box_iou(a, b) -> float:
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    union = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter
    return inter / union if union > 0 else 0.0


def _box_ios(a, b) -> float:
    """交集占较小框面积比（包含度，与端侧 boxIoS 同语义）"""
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    smaller = min((ax2 - ax1) * (ay2 - ay1), (bx2 - bx1) * (by2 - by1))
    return inter / smaller if smaller > 0 else 0.0


def _nms(cands, iou_thr: float, max_keep: int):
    """置信度降序 + IoU/包含抑制（与 yoloDecode.ts nms 一致）"""
    kept = []
    for c in sorted(cands, key=lambda x: -x.confidence):
        if len(kept) >= max_keep:
            break
        overlap = any(
            _box_iou(c.box, k.box) > iou_thr or _box_ios(c.box, k.box) > 0.8
            for k in kept
        )
        if not overlap:
            kept.append(c)
    return kept


def _decode_output(output: np.ndarray, score_thr: float):
    """YOLOv8 系输出 [1, 4+C, N]（cx,cy,w,h 为 letterbox 像素坐标）→ NMS 后候选"""
    rows, n = output.shape[0], output.shape[1]
    num_classes = rows - 4
    class _Cand:
        __slots__ = ("cx", "cy", "w", "h", "cls", "conf")

        def __init__(self, cx, cy, w, h, cls, conf):
            self.cx, self.cy, self.w, self.h = cx, cy, w, h
            self.cls, self.conf = cls, conf

        @property
        def box(self):
            return (self.cx - self.w / 2, self.cy - self.h / 2,
                    self.cx + self.w / 2, self.cy + self.h / 2)

        @property
        def confidence(self):
            return self.conf

    cands: list[_Cand] = []
    for i in range(n):
        scores = output[4:, i]
        best_cls = int(np.argmax(scores))
        best = float(scores[best_cls])
        if best < score_thr:
            continue
        cands.append(_Cand(
            float(output[0, i]), float(output[1, i]),
            float(output[2, i]), float(output[3, i]),
            best_cls, best,
        ))
    return _nms(cands, _IOU_THRESHOLD, _MAX_KEEP)


def detect_image(req: YoloDetectRequest) -> YoloDetectResponse:
    import cv2

    # 1. 入参校验：base64 解码 ≤5MB，图片可解码（失败 400）
    raw = _decode_base64(req.image_base64)
    buf = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("图片解码失败（非图片数据或格式不支持）")
    src_h, src_w = img.shape[:2]

    # 2. 推理（模型缺失/异常 → 3001）
    session = _load_session()
    tensor, scale, dw, dh = _preprocess(img, _INPUT_SIZE)
    try:
        outputs = session.run(None, {session.get_inputs()[0].name: tensor})
    except Exception as e:  # noqa: BLE001
        log.warning("YOLO 推理失败: %s", e)
        raise AiRouteExhausted("yolo_detect", "MANUAL") from e
    output = np.asarray(outputs[0])
    if output.ndim != 3 or output.shape[0] != 1:
        # 形状异常：按端侧约定尝试转置（[1,N,4+C] 兼容）
        if output.ndim == 3 and output.shape[1] == _INPUT_SIZE:
            output = output.transpose(0, 2, 1)
        else:
            raise AiRouteExhausted("yolo_detect", "MANUAL")

    # 3. 解码 + NMS（与 yoloDecode.ts 同逻辑）
    score_thr = req.score_threshold if req.score_threshold is not None else _DEFAULT_SCORE
    kept = _decode_output(output[0], score_thr)

    # 4. letterbox 逆变换 → 原图像素框（clamp 防越界）
    def to_src(v: float) -> float:
        return (v - dw) / scale

    detections: list[YoloDetectionItem] = []
    for c in kept:
        x1 = max(0, min(src_w, round(to_src(c.cx - c.w / 2))))
        y1 = max(0, min(src_h, round(to_src(c.cy - c.h / 2))))
        x2 = max(0, min(src_w, round(to_src(c.cx + c.w / 2))))
        y2 = max(0, min(src_h, round(to_src(c.cy + c.h / 2))))
        if x2 <= x1 or y2 <= y1:
            continue
        detections.append(YoloDetectionItem(
            class_name=_LABELS[c.cls] if c.cls < len(_LABELS) else "other",
            confidence=round(c.conf, 2),
            bbox=[x1, y1, x2, y2],
            center=[(x1 + x2) / 2, (y1 + y2) / 2],
        ))

    # 5. 标注图（检测框 + 类别文本 → JPEG base64）
    annotated = None
    if detections:
        for d in detections:
            x1, y1, x2, y2 = d.bbox
            cv2.rectangle(img, (x1, y1), (x2, y2), (33, 150, 243), 2)
            label = f"{d.class_name} {d.confidence:.2f}"
            (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.55, 1)
            cv2.rectangle(img, (x1, max(0, y1 - th - 6)), (x1 + tw + 6, y1), (33, 150, 243), -1)
            cv2.putText(img, label, (x1 + 3, max(th + 2, y1 - 3)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)
        ok, encoded = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), 85])
        if ok:
            annotated = base64.b64encode(encoded.tobytes()).decode("ascii")

    log.info("YOLO detect hits=%d conf=%.2f size=%dx%d model=%s",
             len(detections), score_thr, src_w, src_h, _MODEL_NAME)
    return YoloDetectResponse(
        detections=detections,
        image_width=src_w,
        image_height=src_h,
        annotated_image_base64=annotated,
        model_name=_MODEL_NAME,
        degraded=False,
    )

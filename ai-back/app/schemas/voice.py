"""schemas.voice · F7 语音碳管家出入参（Sprint 0 冻结 + V44 图片扫描升级 + V46 本地 ASR）

ASR/TTS 由云服务/电话网关完成（开放问题待定），本服务承接「文本→意图与槽位」解析；
图片扫描升级：VoiceParseRequest 可选携带 image_base64 → 服务内先做 YOLO 检测，
返回标注图（识别后图片）+ 检测明细，LLM 结合 RAG 知识作答；
降级路径：解析失败/低置信度 → 前端回退文本输入表单。
V46 本地 ASR：VoiceAsrRequest（WAV 16k 单声道 base64）→ vosk 离线转写文本。
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class VoiceParseRequest(BaseModel):
    session_id: str = Field(description="语音会话 ID（biz_voice_session）")
    user_id: int
    text: str = Field(min_length=1, description="ASR 转写文本（或用户直接输入的文本）")
    context: list[dict] = Field(default_factory=list,
                                description="多轮上下文 [{role,text}]，槽位补全用")
    image_base64: str | None = Field(
        default=None, description="可选：图片 base64（data URL 或纯 base64，≤5MB）；携带时先做 YOLO 检测")


class VoiceAsrRequest(BaseModel):
    audio_base64: str = Field(description="WAV 16k 单声道 16bit PCM base64（前端 AudioContext 解码重采样生成）")


class VoiceAsrResponse(BaseModel):
    text: str = Field(default="", description="转写文本（空串=未识别到语音/静音）")


class VoiceSlots(BaseModel):
    category: str | None = Field(default=None, description="品类")
    appoint_time: str | None = Field(default=None, description="预约时间（自然语言，Java 侧标准化）")
    address: str | None = Field(default=None, description="地址（空则取用户默认地址）")


class VoiceDetectionItem(BaseModel):
    """图片扫描检测项（识别后图片上框出的目标）"""

    class_name: str = Field(description="类别（glass/metal/paper/plastic/kitchen/other）")
    confidence: float = Field(ge=0, le=1, description="置信度")
    bbox: list[int] = Field(min_length=4, max_length=4, description="原图坐标 [x1,y1,x2,y2]")


class VoiceParseResponse(BaseModel):
    intent: Literal["BOOK_RECYCLING", "QUERY_ORDER", "QUERY_CARBON_ACCOUNT",
                    "CANCEL_ORDER", "SMALL_TALK", "UNKNOWN"] = Field(description="识别意图")
    slots: VoiceSlots
    confidence: float = Field(ge=0, le=1, description="低于阈值前端回退文本输入")
    reply_text: str = Field(description="TTS 播报/文字回复内容")
    need_slots: list[str] = Field(default_factory=list, description="仍缺失的必填槽位，驱动多轮追问")
    model_name: str = ""
    degraded: bool = False
    # ---- V44 图片扫描升级（仅携带图片时返回） ----
    annotated_image_base64: str | None = Field(
        default=None, description="识别后图片（标注检测框的 JPEG base64）；未携带图片为 None")
    detections: list[VoiceDetectionItem] = Field(
        default_factory=list, description="图片扫描检测明细（空=未携带图片/无目标）")

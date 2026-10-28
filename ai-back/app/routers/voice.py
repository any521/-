"""routers.voice · F7 语音碳管家（Java /api/ai/voice/parse → 本服务 /v1/voice/parse；
Java /api/ai/voice/asr → 本服务 /v1/voice/asr 本地 vosk 转写）"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.logging import trace_id_var
from app.core.security import require_internal_token
from app.schemas.common import CODE_AI_UNAVAILABLE, Result
from app.schemas.voice import (
    VoiceAsrRequest,
    VoiceAsrResponse,
    VoiceParseRequest,
    VoiceParseResponse,
)
from app.services import asr_service, voice_service

router = APIRouter(prefix="/v1/voice", tags=["F7 语音解析"],
                   dependencies=[Depends(require_internal_token)])


@router.post("/parse", response_model=Result[VoiceParseResponse])
def parse(req: VoiceParseRequest) -> Result[VoiceParseResponse]:
    return Result.ok(voice_service.parse(req), trace_id_var.get())


@router.post("/asr", response_model=Result[VoiceAsrResponse])
def asr(req: VoiceAsrRequest) -> Result[VoiceAsrResponse]:
    """本地 vosk 离线转写：模型缺失/音频异常降级 3001（Java 侧提示改用文字输入），不抛 500"""
    try:
        text = asr_service.transcribe(req.audio_base64)
        return Result.ok(VoiceAsrResponse(text=text), trace_id_var.get())
    except Exception as e:  # noqa: BLE001（模型缺失/格式不符/推理异常统一降级）
        return Result.fail(CODE_AI_UNAVAILABLE, f"ASR 不可用: {e}", trace_id_var.get())

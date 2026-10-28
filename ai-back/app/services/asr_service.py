"""services.asr_service · F7 语音转写（本地 vosk 离线 ASR，CPU 推理）

背景：浏览器 Web Speech API（Chrome 系走 Google 服务器）在国内网络不可达，
端侧录音（WAV 16k 单声道 PCM）经 Java /api/ai/voice/asr 转发到此转写。

模型：vosk-model-small-cn-0.22（约 42MB，hf-mirror 构建时下载至 /app/models）；
模型缺失/加载失败/推理异常一律抛 RuntimeError，由路由层降级 3001（Java 提示改用文字输入）。
"""
from __future__ import annotations

import io
import os
import wave
from pathlib import Path

from app.core.logging import get_logger

log = get_logger("asr_service")

# 模型目录：容器内 /app/models（Dockerfile 构建时下载）；本地开发可设 AI_ASR_MODEL_DIR 覆盖
_MODEL_DIR = Path(os.environ.get("AI_ASR_MODEL_DIR", "/app/models"))
_MODEL_NAME = "vosk-model-small-cn-0.22"

try:
    from vosk import KaldiRecognizer, Model as VoskModel  # type: ignore
    _VOSK_OK = True
except Exception:  # vosk 未安装（requirements 缺失）
    KaldiRecognizer = None  # type: ignore
    VoskModel = None  # type: ignore
    _VOSK_OK = False

_model: VoskModel | None = None
_model_error: str | None = None


def _load_model() -> VoskModel | None:
    """懒加载 vosk 模型：首次调用时加载并缓存；失败记录原因，后续直接短路"""
    global _model, _model_error
    if _model is not None or _model_error is not None:
        return _model
    if not _VOSK_OK:
        _model_error = "vosk 依赖未安装"
        log.error("F7 ASR 不可用: %s", _model_error)
        return None
    model_path = _MODEL_DIR / _MODEL_NAME
    if not model_path.is_dir() or not (model_path / "conf").is_dir():
        _model_error = f"模型缺失: {model_path}"
        log.error("F7 ASR 不可用: %s", _model_error)
        return None
    try:
        _model = VoskModel(str(model_path))
        log.info("F7 ASR 模型加载完成: %s", model_path)
        return _model
    except Exception as e:  # noqa: BLE001（模型损坏/加载异常）
        _model_error = f"模型加载失败: {e}"
        log.error("F7 ASR 不可用: %s", _model_error)
        return None


def transcribe(audio_base64: str) -> str:
    """WAV 16k 单声道 PCM base64 → 转写文本（空串=未识别到语音/静音）。

    校验：音频必须是 16kHz 单声道 16bit PCM（前端 AudioContext 解码重采样保证）；
    不满足/损坏/模型不可用一律抛 RuntimeError，由路由层降级，不返回错误文本。
    """
    model = _load_model()
    if model is None:
        raise RuntimeError(_model_error or "ASR 模型不可用")

    import base64
    try:
        raw = base64.b64decode(audio_base64)
    except Exception as e:  # noqa: BLE001
        raise RuntimeError(f"音频 base64 解码失败: {e}") from e
    if len(raw) < 44:
        raise RuntimeError("音频数据过短")

    try:
        with wave.open(io.BytesIO(raw), "rb") as wav:
            if wav.getframerate() != 16000 or wav.getnchannels() != 1:
                raise RuntimeError(
                    f"音频格式不符: rate={wav.getframerate()} channels={wav.getnchannels()}（需 16000/1）")
            pcm = wav.readframes(wav.getnframes())
    except RuntimeError:
        raise
    except Exception as e:  # noqa: BLE001（非标准 WAV 头）
        raise RuntimeError(f"WAV 解析失败: {e}") from e
    if not pcm:
        return ""

    try:
        rec = KaldiRecognizer(model, 16000)
        rec.AcceptWaveform(pcm)
        import json
        result = json.loads(rec.FinalResult())
        text = (result.get("text") or "").strip()
        log.info("F7 ASR 转写完成 len=%d text=%s", len(text), text[:30])
        return text
    except Exception as e:  # noqa: BLE001（推理异常）
        log.warning("F7 ASR 推理异常: %s", e)
        raise RuntimeError(f"ASR 推理失败: {e}") from e

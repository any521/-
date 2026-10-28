package com.dianfei.ai.voice.controller;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.voice.VoiceStewardService;
import com.dianfei.ai.voice.dto.VoiceParseDTO;
import com.dianfei.ai.voice.entity.VoiceSession;
import com.dianfei.ai.voice.vo.VoiceParseVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.UserContext;
import com.dianfei.common.utils.RedisUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * F7 语音碳管家（12 章 POST /api/ai/voice/parse）：文本→意图槽位→多轮补槽→自动成单；
 * ASR（POST /api/ai/voice/asr）：端侧录音（WAV 16k 单声道 base64）→ 本地 vosk 离线转写，
 * 替代国内不可达的浏览器 Web Speech API（Chrome 系走 Google 服务器），全端统一可用。
 */
@Slf4j
@Tag(name = "AI-语音碳管家（F7）")
@RestController
@RequestMapping("/api/ai/voice")
@RequiredArgsConstructor
public class VoiceAiController {

    /** ASR 频控：每用户 30s 窗口最多 6 次（一次一句录音，防刷量） */
    private static final String ASR_FREQ_KEY_PREFIX = "ai:voice:asr:freq:";
    private static final int ASR_FREQ_LIMIT = 6;
    private static final Duration ASR_FREQ_WINDOW = Duration.ofSeconds(30);
    /** 录音上限：WAV 16k 单声道 60s ≈ 1.9MB，base64 ≈ 2.6MB */
    private static final int MAX_AUDIO_B64 = 3 * 1024 * 1024;

    private final VoiceStewardService voiceStewardService;
    private final AiServiceClient aiServiceClient;
    private final RedisUtils redisUtils;

    @Operation(summary = "语音意图解析：多轮补槽，槽位齐全自动创建回收单（orderId 回填）")
    @PostMapping("/parse")
    public Result<VoiceParseVO> parse(@Valid @RequestBody VoiceParseDTO dto) {
        return Result.success(voiceStewardService.parse(UserContext.getUserId(), dto));
    }

    @Operation(summary = "语音转写（ASR）：WAV 16k 单声道 base64 → 文本")
    @PostMapping("/asr")
    public Result<String> asr(@Valid @RequestBody AsrDTO dto) {
        Long userId = UserContext.getUserId();
        long freq = redisUtils.increment(ASR_FREQ_KEY_PREFIX + userId, ASR_FREQ_WINDOW);
        if (freq > ASR_FREQ_LIMIT) {
            return Result.failed(ResultCode.TOO_MANY_REQUESTS, "语音识别过于频繁，请稍后再试");
        }
        String audioBase64 = dto.getAudioBase64();
        if (audioBase64.length() > MAX_AUDIO_B64) {
            return Result.failed(ResultCode.PARAM_ERROR, "录音过长，请控制在 60 秒内");
        }
        try {
            AiContracts.VoiceAsrResponse resp = aiServiceClient.voiceAsr(
                    AiContracts.VoiceAsrRequest.builder().audioBase64(audioBase64).build());
            return Result.success(resp.getText() == null ? "" : resp.getText());
        } catch (AiServiceException e) {
            // 本地 ASR 不可用（模型缺失/推理异常）：降级为空文本，前端提示改用文字输入
            log.warn("[F7] ASR 转写不可用 : {}", e.getMessage());
            return Result.failed(ResultCode.AI_SERVICE_UNAVAILABLE, "语音识别暂不可用，请改用文字输入");
        }
    }

    @Operation(summary = "会话轨迹回显（逐轮文本/意图/槽位/回复）")
    @GetMapping("/session/{sessionId}")
    public Result<List<VoiceSession>> session(@PathVariable String sessionId) {
        return Result.success(voiceStewardService.sessionTurns(UserContext.getUserId(), sessionId));
    }

    @Data
    public static class AsrDTO {
        /** WAV 16k 单声道 PCM base64（前端 AudioContext 解码重采样生成） */
        @NotBlank(message = "录音数据不能为空")
        private String audioBase64;
    }
}

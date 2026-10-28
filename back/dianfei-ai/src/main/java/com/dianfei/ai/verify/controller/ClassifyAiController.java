package com.dianfei.ai.verify.controller;

import cn.hutool.core.util.StrUtil;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.verify.dto.ClassifyDTO;
import com.dianfei.ai.verify.vo.ClassifyVO;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.UserContext;
import com.dianfei.common.utils.RedisUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Base64;

/**
 * CAM 云端精分类（契约 C6-2 POST /api/ai/classify）：裁剪目标图 → ai-back /v1/classify/detail；
 * 失败/超时/熔断一律 3001（对齐 D15），前端 Toast 兜底，不抛 AI 异常。
 */
@Slf4j
@Tag(name = "AI-精分类（CAM）")
@RestController
@RequestMapping("/api/ai/classify")
@RequiresRole("USER")
@RequiredArgsConstructor
public class ClassifyAiController {

    /** 裁剪图上限（契约 C6-3）：base64 解码后 ≤300KB */
    private static final int MAX_CROP_BYTES = 300 * 1024;
    /** 频控（契约 C6-4 调整）：每用户 30s 滑动窗口最多 FREQ_LIMIT 次（连拍/多目标扫描可用，防持续刷量）
     * 实时扫描场景前端已做 30s 配额节流（自动触发≤3 次），手动重识别另占配额，放宽到 8 次避免误伤； */
    private static final String FREQ_KEY_PREFIX = "ai:classify:freq:";
    private static final int FREQ_LIMIT = 8;
    private static final Duration FREQ_WINDOW = Duration.ofSeconds(30);

    private final AiServiceClient aiServiceClient;
    private final RedisUtils redisUtils;

    @Operation(summary = "云端精分类：裁剪目标图 base64 → 具体物品名 + 8 类枚举 + 当日报价（C6-2）")
    @PostMapping
    public Result<ClassifyVO> classify(@Valid @RequestBody ClassifyDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 裁剪图规格校验：base64 ≤300KB（契约 C6-3，双层防线，超限 400）
        if (!isCropWithinLimit(dto.getImageUrl())) {
            return Result.failed(ResultCode.PARAM_ERROR, "裁剪图超过 300KB 上限（契约 C6-3）");
        }

        // 2. 频控（契约 C6-4）：每用户 30s 窗口内超限返回 429 语义，不抛异常
        long freqCount = redisUtils.increment(FREQ_KEY_PREFIX + userId, FREQ_WINDOW);
        if (freqCount > FREQ_LIMIT) {
            log.warn("[CAM] 精分类频控命中 userId={} count={}", userId, freqCount);
            return Result.failed(ResultCode.TOO_MANY_REQUESTS, "识别过于频繁，请稍后再试");
        }

        // 3. 云端精分类：超时/异常/3001 一律降级 3001（契约 C6-5），前端 Toast 兜底
        AiContracts.ClassifyResponse resp;
        try {
            resp = aiServiceClient.classifyDetail(AiContracts.ClassifyRequest.builder()
                    .imageUrl(dto.getImageUrl())
                    .categoryHint(StrUtil.blankToDefault(dto.getCategoryHint(), null))
                    .build());
        } catch (AiServiceException e) {
            log.warn("[CAM] 精分类 AI 不可用 fallback={} userId={}", e.getFallback(), userId);
            return Result.failed(ResultCode.AI_SERVICE_UNAVAILABLE, "AI 服务不可用");
        }
        return Result.success(toVO(resp));
    }

    private boolean isCropWithinLimit(String imageUrl) {
        if (imageUrl == null) {
            return false;
        }
        try {
            String data = imageUrl;
            if (data.startsWith("data:")) {
                int comma = data.indexOf(',');
                if (comma < 0) {
                    return false;
                }
                data = data.substring(comma + 1);
            }
            return Base64.getDecoder().decode(data).length <= MAX_CROP_BYTES;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ClassifyVO toVO(AiContracts.ClassifyResponse resp) {
        ClassifyVO vo = new ClassifyVO();
        vo.setItemName(resp.getItemName());
        vo.setCategory(resp.getCategory());
        vo.setConfidence(resp.getConfidence());
        vo.setPurity(resp.getPurity());
        vo.setAdvice(resp.getAdvice());
        vo.setPricePerKg(resp.getPricePerKg());
        vo.setDegraded(Boolean.TRUE.equals(resp.getDegraded()));
        vo.setElapsedMs(resp.getElapsedMs());
        vo.setModelName(resp.getModelName());
        return vo;
    }
}

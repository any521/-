package com.dianfei.ai.pricing.controller;

import com.dianfei.ai.pricing.PricingAiService;
import com.dianfei.ai.pricing.dto.PricingApplyDTO;
import com.dianfei.ai.pricing.vo.PricingSuggestVO;
import com.dianfei.business.recycle.entity.RecyclePrice;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F5 动态定价管理端（05 章 5.7 / E7）：suggest 只读建议（AI 系数 × 基础价，不落库）
 * → 管理员确认后 apply 应用（旧价快照入 Redis）→ rollback 一键回滚快照价。
 * AI 不可用时 suggest 返回系数 1.0 + degraded，人工仍可按基础价维护（D15）。
 */
@Tag(name = "AI-动态定价（F5）")
@RestController
@RequestMapping("/api/ai/pricing")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class PricingAiController {

    private final PricingAiService pricingAiService;

    @Operation(summary = "定价建议：近 7 日供需 → AI 系数（0.8-1.2 夹逼）× 基础价，不落库")
    @GetMapping("/suggest")
    public Result<PricingSuggestVO> suggest(@RequestParam Long areaId,
                                            @RequestParam String category) {
        return Result.success(pricingAiService.suggest(areaId, category));
    }

    @Operation(summary = "应用系数：旧价快照入 Redis（7 天）→ 新价立即生效")
    @PostMapping("/apply")
    public Result<RecyclePrice> apply(@Valid @RequestBody PricingApplyDTO dto) {
        return Result.success(pricingAiService.apply(dto));
    }

    @Operation(summary = "一键回滚：恢复应用前快照价（快照过期或未应用过则报错）")
    @PostMapping("/rollback")
    public Result<RecyclePrice> rollback(@RequestParam String category) {
        return Result.success(pricingAiService.rollback(category));
    }
}

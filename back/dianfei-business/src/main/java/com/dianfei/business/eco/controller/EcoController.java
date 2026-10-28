package com.dianfei.business.eco.controller;

import com.dianfei.business.eco.service.EcoTaskService;
import com.dianfei.business.eco.vo.EcoAchievementVO;
import com.dianfei.business.eco.vo.EcoClaimVO;
import com.dianfei.business.eco.vo.EcoSignResultVO;
import com.dianfei.business.eco.vo.EcoSignStatusVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 · 环保成就（LoopFill B5 创意 C1，契约 C5）：
 * 每日签到（+积分走碳账本）/ 本月签到日历 / 成就达成态 / 成就领取（幂等）。
 */
@Tag(name = "用户端-环保成就")
@RestController
@RequestMapping("/api/eco")
@RequiredArgsConstructor
public class EcoController {

    private final EcoTaskService ecoTaskService;

    @Operation(summary = "每日签到（每天一次 +5 积分，重复签到报错；连续 7 天自动解锁 SIGN_SEVEN）")
    @Idempotent(prefix = "eco:sign", message = "签到处理中，请勿重复提交")
    @PostMapping("/sign-in")
    public Result<EcoSignResultVO> signIn() {
        return Result.success(ecoTaskService.signIn(UserContext.getUserId()));
    }

    @Operation(summary = "签到状态（本月签到日历 + 连续天数 + 累计 + 单次积分）")
    @GetMapping("/sign-in/status")
    public Result<EcoSignStatusVO> signStatus() {
        return Result.success(ecoTaskService.signStatus(UserContext.getUserId()));
    }

    @Operation(summary = "成就列表（全量 + 达成态 unlocked/claimed）")
    @GetMapping("/achievements")
    public Result<List<EcoAchievementVO>> achievements() {
        return Result.success(ecoTaskService.achievements(UserContext.getUserId()));
    }

    @Operation(summary = "领取成就奖励（未达成报错；已领取幂等返回）")
    @PostMapping("/achievements/{code}/claim")
    public Result<EcoClaimVO> claim(@PathVariable String code) {
        return Result.success(ecoTaskService.claim(UserContext.getUserId(), code));
    }
}

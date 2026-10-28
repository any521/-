package com.dianfei.business.badge.controller;

import com.dianfei.business.badge.service.BadgeService;
import com.dianfei.business.badge.vo.BadgeExchangeVO;
import com.dianfei.business.badge.vo.BadgeVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 · 成就徽章（积分市场合并升级）：上架徽章浏览（登录态回填 owned）、
 * 积分兑换（余额不足 2006，防重复）、我的徽章。兑换即到账，无物流状态。
 */
@Tag(name = "用户端-成就徽章")
@RestController
@RequestMapping("/api/badge")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @Operation(summary = "上架徽章分页（sort 升序；登录态回填 owned）")
    @GetMapping("/list")
    public Result<PageVO<BadgeVO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String keyword) {
        return Result.success(badgeService.pageOnSale(UserContext.getUserId(), keyword, pageNum, pageSize));
    }

    @Operation(summary = "积分兑换徽章（虚拟商品即到账；余额不足 2006；重复兑换报错）")
    @Idempotent(prefix = "badge:exchange", message = "兑换处理中，请勿重复提交")
    @PostMapping("/{id}/exchange")
    public Result<BadgeExchangeVO> exchange(@PathVariable Long id) {
        return Result.success(badgeService.exchange(UserContext.getUserId(), id));
    }

    @Operation(summary = "我的徽章分页（兑换时间倒序）")
    @GetMapping("/mine")
    public Result<PageVO<BadgeExchangeVO>> mine(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(badgeService.pageMine(UserContext.getUserId(), pageNum, pageSize));
    }
}

package com.dianfei.business.evaluate.controller;

import com.dianfei.business.evaluate.dto.ReviewCreateDTO;
import com.dianfei.business.evaluate.entity.OrderReview;
import com.dianfei.business.evaluate.service.OrderReviewService;
import com.dianfei.business.evaluate.vo.ReviewCheckVO;
import com.dianfei.business.evaluate.vo.ReviewVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
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
 * 用户端 · 订单评价（GapFill B2）：提交评价 / 我的评价分页 / 本单是否已评价
 * 契约：POST /api/reviews + GET /api/reviews/mine + GET /api/reviews/check
 */
@Tag(name = "用户端-订单评价")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final OrderReviewService reviewService;

    @Operation(summary = "提交订单评价（仅已完成订单、每单每人一次）")
    @Idempotent(prefix = "review:submit")
    @OperLog(value = "提交订单评价", module = "用户端-订单评价")
    @PostMapping
    public Result<OrderReview> submit(@Valid @RequestBody ReviewCreateDTO dto) {
        return Result.success(reviewService.submit(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的评价分页（连业务订单号）")
    @GetMapping("/mine")
    public Result<PageVO<ReviewVO>> mine(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reviewService.pageMine(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "本单是否已评价（订单详情页回显星级）")
    @GetMapping("/check")
    public Result<ReviewCheckVO> check(@RequestParam String orderType, @RequestParam Long orderId) {
        return Result.success(reviewService.check(UserContext.getUserId(), orderType, orderId));
    }
}

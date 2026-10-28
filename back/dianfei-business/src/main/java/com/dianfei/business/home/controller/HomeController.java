package com.dianfei.business.home.controller;

import com.dianfei.business.home.service.HomeSummaryService;
import com.dianfei.business.home.vo.HomeSummaryVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 · 首页摘要（G7）：积分/累计减排/本月减排/最新公告（12 章契约 /api/home/summary）
 */
@Tag(name = "用户端-首页摘要")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeSummaryService summaryService;

    @Operation(summary = "首页摘要：积分/累计减排/本月减排/最新公告")
    @GetMapping("/summary")
    public Result<HomeSummaryVO> summary() {
        return Result.success(summaryService.summary(UserContext.getUserId()));
    }
}

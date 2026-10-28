package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.service.StaffPerformanceService;
import com.dianfei.business.staff.vo.PerformanceVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端 · 绩效（G10）：月度绩效聚合（12 章契约 /api/staff/performance）
 */
@Tag(name = "员工端-绩效")
@RestController
@RequestMapping("/api/staff/performance")
@RequiredArgsConstructor
public class StaffPerformanceController {

    private final StaffPerformanceService performanceService;

    @Operation(summary = "月度绩效（month=yyyy-MM，缺省当月；days 为近 7 日完成曲线）")
    @GetMapping
    public Result<PerformanceVO> month(@RequestParam(required = false) String month) {
        return Result.success(performanceService.monthPerformance(UserContext.getUserId(), month));
    }
}

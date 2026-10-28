package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.service.StaffRouteService;
import com.dianfei.business.staff.vo.RoutePlanVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端 · 智能路线（G11）：今日路线（12 章契约 /api/staff/route/today）
 */
@Tag(name = "员工端-智能路线")
@RestController
@RequestMapping("/api/staff/route")
@RequiredArgsConstructor
public class StaffRouteController {

    private final StaffRouteService routeService;

    @Operation(summary = "今日智能路线（预约时间贪心；无单返回空 stops）")
    @GetMapping("/today")
    public Result<RoutePlanVO> today() {
        return Result.success(routeService.todayRoute(UserContext.getUserId()));
    }
}

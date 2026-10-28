package com.dianfei.business.dashboard.controller;

import com.dianfei.business.dashboard.service.DashboardService;
import com.dianfei.business.dashboard.vo.HomeStatsVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 · 首页平台环保数据（复用数据大屏聚合口径：回收订单 / 碳减排 / 注册用户 / 维修次数）
 */
@Tag(name = "用户端-首页环保数据")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeStatsController {

    private final DashboardService dashboardService;

    @Operation(summary = "平台环保数据（累计回收订单 / 累计碳减排 / 注册用户数 / 维修服务次数）")
    @GetMapping("/stats")
    public Result<HomeStatsVO> stats() {
        HomeStatsVO vo = new HomeStatsVO();
        vo.setRecycleOrders(dashboardService.overview().getRecycleTotalOrders());
        vo.setCarbonReduced(dashboardService.overview().getCarbonReducedTotal());
        vo.setTotalUsers(dashboardService.summary().getTotalUsers());
        vo.setRepairOrders(dashboardService.overview().getRepairTotalOrders());
        return Result.success(vo);
    }
}

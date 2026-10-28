package com.dianfei.business.dashboard.controller;

import com.dianfei.business.dashboard.service.DashboardExportService;
import com.dianfei.business.dashboard.service.DashboardService;
import com.dianfei.business.dashboard.vo.DashboardOverviewVO;
import com.dianfei.business.dashboard.vo.DashboardSummaryVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 后台 · 数据大屏（C5，business/dashboard）：
 * 总览聚合（1s 快照缓存供大屏轮询）+ EasyExcel 台账/月报导出
 */
@Tag(name = "后台-数据大屏")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final DashboardExportService dashboardExportService;

    @Operation(summary = "大屏总览：回收/垃圾投放/维修/碳减排/片区排行/月度报表（1s 缓存快照）")
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        return Result.success(dashboardService.overview());
    }

    @Operation(summary = "工作台摘要（今日单量/重量/碳/用户数/周趋势/品类占比/片区排名，1s 缓存快照）")
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.success(dashboardService.summary());
    }

    @Operation(summary = "台账/月报导出（EasyExcel 双 Sheet：回收台账 + 近 12 个月月报）")
    @OperLog(value = "台账月报导出", module = "数据大屏")
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("点废成金台账-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        dashboardExportService.exportLedger(response.getOutputStream());
    }
}

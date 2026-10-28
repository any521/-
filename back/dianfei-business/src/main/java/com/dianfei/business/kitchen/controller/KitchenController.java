package com.dianfei.business.kitchen.controller;

import com.dianfei.business.kitchen.dto.KitchenReportCreateDTO;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.service.KitchenReportService;
import com.dianfei.business.kitchen.vo.KitchenAreaStatsVO;
import com.dianfei.business.kitchen.vo.KitchenMyStatsVO;
import com.dianfei.common.annotation.Idempotent;
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
 * 用户端 · 垃圾投放（B4）：上报（20-23 点服务端校验）/ 上报记录 / 个人累计 / 片区看板
 * （12 章契约 POST /api/kitchen/reports、GET /api/kitchen/stats/area）
 */
@Tag(name = "用户端-垃圾投放")
@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenReportService reportService;

    @Operation(summary = "投放上报（仅限每晚 20-23 点，偏离户均转风控冻结）")
    @Idempotent(prefix = "kitchen:report")
    @PostMapping("/reports")
    public Result<KitchenReport> report(@Valid @RequestBody KitchenReportCreateDTO dto) {
        return Result.success(reportService.report(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的上报记录分页")
    @GetMapping("/reports")
    public Result<PageVO<KitchenReport>> pageMine(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reportService.pageMine(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "个人累计（总袋数/今日袋数/冻结待复核）")
    @GetMapping("/stats/me")
    public Result<KitchenMyStatsVO> myStats() {
        return Result.success(reportService.myStats(UserContext.getUserId()));
    }

    @Operation(summary = "片区看板（当日袋数对比清运阈值）")
    @GetMapping("/stats/area")
    public Result<KitchenAreaStatsVO> areaStats(@RequestParam Long areaId) {
        return Result.success(reportService.areaStats(areaId));
    }
}

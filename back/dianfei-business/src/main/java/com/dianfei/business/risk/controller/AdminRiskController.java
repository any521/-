package com.dianfei.business.risk.controller;

import com.dianfei.business.risk.entity.RiskAlert;
import com.dianfei.business.risk.service.RiskAlertService;
import com.dianfei.business.risk.vo.BrushRiskVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台 · 风控中心（C4）：告警复核回写积分状态、恶意刷单检测；
 * 刷单处置（冻结/清零）复用 AdminCarbonController 既有接口
 */
@Tag(name = "后台-风控中心")
@RestController
@RequestMapping("/api/admin/risk")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminRiskController {

    private final RiskAlertService riskAlertService;

    @Operation(summary = "风控告警分页（状态/片区/业务类型过滤，待复核优先）")
    @GetMapping("/alerts")
    public Result<PageVO<RiskAlert>> alerts(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long areaId,
                                            @RequestParam(required = false) String bizType) {
        return Result.success(riskAlertService.pageAlerts(status, areaId, bizType, pageNum, pageSize));
    }

    @Operation(summary = "告警复核：pass=true 解冻发放，pass=false 驳回扣除（账本回写同事务）")
    @OperLog(value = "风控告警复核", module = "风控中心")
    @PutMapping("/alerts/{id}/review")
    public Result<RiskAlert> review(@PathVariable Long id, @RequestParam boolean pass) {
        return Result.success(riskAlertService.review(id, pass));
    }

    @Operation(summary = "恶意刷单检测：近 hours 小时内有效兑换单数 ≥ threshold 的用户")
    @GetMapping("/brush-check")
    public Result<List<BrushRiskVO>> brushCheck(@RequestParam(defaultValue = "24") Integer hours,
                                                @RequestParam(defaultValue = "10") Integer threshold) {
        return Result.success(riskAlertService.brushCheck(hours, threshold));
    }
}

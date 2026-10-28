package com.dianfei.business.kitchen.controller;

import com.dianfei.business.kitchen.dto.KitchenReportAuditDTO;
import com.dianfei.business.kitchen.dto.KitchenTaskFinishDTO;
import com.dianfei.business.kitchen.entity.KitchenTask;
import com.dianfei.business.kitchen.service.KitchenReportService;
import com.dianfei.business.kitchen.service.KitchenTaskService;
import com.dianfei.business.kitchen.vo.KitchenRecordVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端 · 环卫员（B4）：清运任务查看（按 biz_staff 片区）/ 完成填报归档 → 当日上报统一发分；
 * 投放记录审核（本片区待审核列表 + 通过发分/驳回）
 */
@Tag(name = "员工端-厨余清运")
@RestController
@RequestMapping("/api/staff/kitchen")
@RequiredArgsConstructor
public class StaffKitchenController {

    private final KitchenTaskService taskService;
    private final KitchenReportService reportService;

    @Operation(summary = "清运任务列表（本片区，待执行优先，Service 侧按 biz_staff 片区过滤）")
    @GetMapping("/tasks")
    public Result<List<KitchenTask>> myTasks() {
        return Result.success(taskService.myTasks(UserContext.getUserId()));
    }

    @Operation(summary = "完成填报归档（总重量/车号/终点/凭证 → 当日已通过上报发分兜底，幂等）")
    @Idempotent(prefix = "kitchen:finish")
    @PutMapping("/tasks/{id}/finish")
    public Result<KitchenTask> finish(@PathVariable Long id, @Valid @RequestBody KitchenTaskFinishDTO dto) {
        return Result.success(taskService.finish(id, UserContext.getUserId(), dto));
    }

    @Operation(summary = "投放审核列表（本片区 0 待审核，Service 侧按 biz_staff 片区过滤）")
    @GetMapping("/reports")
    public Result<List<KitchenRecordVO>> pendingReports() {
        return Result.success(reportService.staffPendingReports(UserContext.getUserId()));
    }

    @Operation(summary = "投放审核（通过即发分账本幂等；驳回不发分，原因透传用户消息）")
    @Idempotent(prefix = "kitchen:audit")
    @PostMapping("/reports/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody KitchenReportAuditDTO dto) {
        reportService.audit(id, UserContext.getUserId(), dto);
        return Result.success();
    }
}

package com.dianfei.business.repair.controller;

import com.dianfei.business.repair.dto.RepairReportDTO;
import com.dianfei.business.repair.linkage.RepairLinkageService;
import com.dianfei.business.repair.service.RepairOrderService;
import com.dianfei.business.repair.vo.RepairOrderVO;
import com.dianfei.business.repair.vo.RepairTraceVO;
import com.dianfei.business.repair.vo.RepairTransferVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.result.PageVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端 · 维修工单（C1）：技术员接单/导航（经纬度随单返回）、开始检修、提交检修报告
 */
@Tag(name = "员工端-维修工单")
@RestController
@RequestMapping("/api/staff/repair/orders")
@RequiredArgsConstructor
public class StaffRepairController {

    private final RepairOrderService repairOrderService;
    private final RepairLinkageService repairLinkageService;

    @Operation(summary = "本片区待接单列表（含客户姓名/电话）")
    @GetMapping("/pending")
    public Result<PageVO<RepairOrderVO>> pending(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(repairOrderService.pagePending(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "我的工单列表（含导航经纬度与客户姓名/电话）")
    @GetMapping
    public Result<PageVO<RepairOrderVO>> mine(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(repairOrderService.pageStaffOrders(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "员工端全量工单（跨片区、全状态，含片区名；接单仍限本片区）")
    @GetMapping("/all")
    public Result<PageVO<RepairOrderVO>> all(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize,
                                             @RequestParam(required = false) Integer status) {
        return Result.success(repairOrderService.pageAllForStaff(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<RepairOrderVO> detail(@PathVariable Long id) {
        return Result.success(repairOrderService.detailForStaff(id, UserContext.getUserId()));
    }

    @Operation(summary = "接单（0待接单→1已接单）")
    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        repairOrderService.accept(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "上门开始检修（1已接单→2检修中）")
    @PutMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        repairOrderService.startCheck(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "提交检修报告（可修复→完成发分；不可修复→待转回收）")
    @PutMapping("/{id}/report")
    public Result<Void> report(@PathVariable Long id, @Valid @RequestBody RepairReportDTO dto) {
        repairOrderService.submitReport(id, UserContext.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "不可修复件一键转回收（C2：同事务生成回收单+回填 recycle_ref，2检修中→5已转回收）")
    @Idempotent(prefix = "repair:transfer")
    @PostMapping("/{id}/transfer-recycle")
    public Result<RepairTransferVO> transferRecycle(@PathVariable Long id) {
        return Result.success(repairLinkageService.transferToRecycle(id, UserContext.getUserId()));
    }

    @Operation(summary = "维修-回收双单溯源（归档）")
    @GetMapping("/{id}/trace")
    public Result<RepairTraceVO> trace(@PathVariable Long id) {
        return Result.success(repairLinkageService.traceForStaff(id, UserContext.getUserId()));
    }
}

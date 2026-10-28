package com.dianfei.business.repair.controller;

import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.linkage.RepairLinkageService;
import com.dianfei.business.repair.service.RepairOrderService;
import com.dianfei.business.repair.vo.RepairOrderVO;
import com.dianfei.business.repair.vo.RepairTraceVO;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · 维修工单管理（C1/C5 后台页面数据源）：
 * 全量分页（状态/片区/可修复性/单号过滤）、详情（含 F2 预诊与配件）、维修-回收双单溯源
 */
@Tag(name = "后台-维修工单管理")
@RestController
@RequestMapping("/api/admin/repair")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminRepairController {

    private final RepairOrderService repairOrderService;
    private final RepairLinkageService repairLinkageService;

    @Operation(summary = "维修工单分页（status/areaId/repairable/orderNo 过滤）")
    @GetMapping("/orders")
    public Result<PageVO<RepairOrder>> pageOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) Long areaId,
                                                  @RequestParam(required = false) Integer repairable,
                                                  @RequestParam(required = false) String orderNo) {
        return Result.success(repairOrderService.pageAdmin(status, areaId, repairable, orderNo, pageNum, pageSize));
    }

    @Operation(summary = "工单详情（含 F2 预诊解析、检修配件明细）")
    @GetMapping("/orders/{id}")
    public Result<RepairOrderVO> detail(@PathVariable Long id) {
        return Result.success(repairOrderService.detail(id, null));
    }

    @Operation(summary = "维修-回收双单溯源（repair_ref/recycle_ref 双向）")
    @GetMapping("/orders/{id}/trace")
    public Result<RepairTraceVO> trace(@PathVariable Long id) {
        return Result.success(repairLinkageService.trace(id, null));
    }
}

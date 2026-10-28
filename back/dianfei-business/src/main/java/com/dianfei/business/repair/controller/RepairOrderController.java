package com.dianfei.business.repair.controller;

import com.dianfei.business.repair.dto.RepairOrderCreateDTO;
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
 * 用户端 · 维修工单（C1）：下单（含 F2 预诊建议）、查询、取消
 */
@Tag(name = "用户端-维修工单")
@RestController
@RequestMapping("/api/repair/orders")
@RequiredArgsConstructor
public class RepairOrderController {

    private final RepairOrderService repairOrderService;
    private final RepairLinkageService repairLinkageService;

    @Operation(summary = "创建维修工单（返回随单 F2 预诊建议，AI 不可用时 diagnose 为空）")
    @Idempotent(prefix = "repair:create")
    @PostMapping
    public Result<RepairOrderVO> create(@Valid @RequestBody RepairOrderCreateDTO dto) {
        return Result.success(repairOrderService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的维修单分页（含接单员工姓名/电话）")
    @GetMapping
    public Result<PageVO<RepairOrderVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(repairOrderService.pageUserOrders(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "维修单详情（含预诊/配件/科普留痕）")
    @GetMapping("/{id}")
    public Result<RepairOrderVO> detail(@PathVariable Long id) {
        return Result.success(repairOrderService.detail(id, UserContext.getUserId()));
    }

    @Operation(summary = "取消维修单（仅待接单/已接单）")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        repairOrderService.cancel(id, UserContext.getUserId(), reason);
        return Result.success();
    }

    @Operation(summary = "不可修复件一键转回收（C2 冻结契约，限本单技术员；同事务生成回收单+回填 recycle_ref）")
    @Idempotent(prefix = "repair:transfer")
    @PostMapping("/{id}/transfer-recycle")
    public Result<RepairTransferVO> transferRecycle(@PathVariable Long id) {
        return Result.success(repairLinkageService.transferToRecycle(id, UserContext.getUserId()));
    }

    @Operation(summary = "维修-回收双单溯源（归档永久留存，限本人工单）")
    @GetMapping("/{id}/trace")
    public Result<RepairTraceVO> trace(@PathVariable Long id) {
        return Result.success(repairLinkageService.trace(id, UserContext.getUserId()));
    }
}

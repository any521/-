package com.dianfei.business.recycle.controller;

import com.dianfei.business.recycle.dto.RecycleExceptionDTO;
import com.dianfei.business.recycle.dto.RecycleFinishDTO;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.vo.RecycleFinishVO;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
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

import java.util.List;

/**
 * 员工端 · 回收员（B3）：今日订单（按 biz_staff 片区）/ 接单 / 开始 / 完成登记（核算+发分）/ 异常上报
 * （12 章契约 /api/staff/today-orders、/api/staff/orders/{id}/finish、/api/staff/exception）
 */
@Tag(name = "员工端-回收订单")
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffRecycleController {

    private final RecycleOrderService orderService;

    @Operation(summary = "今日订单（指派给我的 + 本片区可抢单，Service 侧按 biz_staff 片区过滤；含客户姓名/电话）")
    @GetMapping("/today-orders")
    public Result<List<RecycleOrderVO>> todayOrders() {
        return Result.success(orderService.todayOrders(UserContext.getUserId()));
    }

    @Operation(summary = "全量订单分页（跨片区、全状态展示，接单仍限本片区；含片区名 areaName）")
    @GetMapping("/orders/page")
    public Result<PageVO<RecycleOrderVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "20") Integer pageSize,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(orderService.pageAllForStaff(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "订单详情（含实收明细）")
    @GetMapping("/orders/{id}")
    public Result<RecycleOrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detailForStaff(id, UserContext.getUserId()));
    }

    @Operation(summary = "接单（0待接单→1已接单，限本片区）")
    @PutMapping("/orders/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        orderService.accept(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "上门开始（1已接单→2进行中）")
    @PutMapping("/orders/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        orderService.start(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "完成登记（2进行中→3完成）：实收明细+去向→核算金额→碳积分入链")
    @Idempotent(prefix = "recycle:finish")
    @PutMapping("/orders/{id}/finish")
    public Result<RecycleFinishVO> finish(@PathVariable Long id, @Valid @RequestBody RecycleFinishDTO dto) {
        return Result.success(orderService.finish(id, UserContext.getUserId(), dto));
    }

    @Operation(summary = "异常上报（1已接单/2进行中→5异常，后台异常单列表复核）")
    @PostMapping("/exception")
    public Result<Void> exception(@Valid @RequestBody RecycleExceptionDTO dto) {
        orderService.reportException(dto.getOrderId(), UserContext.getUserId(), dto.getReason());
        return Result.success();
    }
}

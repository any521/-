package com.dianfei.business.recycle.controller;

import com.dianfei.business.recycle.dto.RecyclePriceSaveDTO;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.entity.RecyclePrice;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.service.RecyclePriceService;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台 · 回收业务管理（B3）：订单查询/详情/改派（异常单复核入口）、报价配置。
 * 改派限本片区在岗回收员，@OperLog 留痕；报价 category 幂等 upsert（09.3 零代码新增品类）
 */
@Tag(name = "后台-回收业务管理")
@RestController
@RequestMapping("/api/admin/recycle")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminRecycleController {

    private final RecycleOrderService orderService;
    private final RecyclePriceService priceService;

    @Operation(summary = "回收订单分页（状态/片区/用户过滤，status=5 即异常单列表）")
    @GetMapping("/orders")
    public Result<PageVO<RecycleOrder>> pageOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) Long areaId,
                                                   @RequestParam(required = false) Long userId) {
        return Result.success(orderService.pageAdmin(status, areaId, userId, pageNum, pageSize));
    }

    @Operation(summary = "订单详情（含实收明细与回收员）")
    @GetMapping("/orders/{id}")
    public Result<RecycleOrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id, null));
    }

    @Operation(summary = "改派（0待接单/1已接单/5异常 → 重置 0 指派本片区在岗回收员）")
    @OperLog(value = "回收订单改派", module = "回收业务管理")
    @PutMapping("/orders/{id}/reassign")
    public Result<Void> reassign(@PathVariable Long id, @RequestParam Long staffId) {
        orderService.reassign(id, staffId);
        return Result.success();
    }

    @Operation(summary = "报价列表（含下架，供配置页）")
    @GetMapping("/price")
    public Result<List<RecyclePrice>> listPrice() {
        return Result.success(priceService.listAll());
    }

    @Operation(summary = "配置报价（category 幂等 upsert）")
    @OperLog(value = "配置回收报价", module = "回收业务管理")
    @PutMapping("/price")
    public Result<RecyclePrice> savePrice(@Valid @RequestBody RecyclePriceSaveDTO dto) {
        return Result.success(priceService.saveByCategory(dto));
    }
}

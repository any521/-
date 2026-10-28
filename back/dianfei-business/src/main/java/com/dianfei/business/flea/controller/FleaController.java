package com.dianfei.business.flea.controller;

import com.dianfei.business.flea.dto.FleaItemCreateDTO;
import com.dianfei.business.flea.dto.FleaOrderCreateDTO;
import com.dianfei.business.flea.dto.FleaReviewDTO;
import com.dianfei.business.flea.service.FleaItemService;
import com.dianfei.business.flea.service.FleaOrderService;
import com.dianfei.business.flea.vo.FleaItemVO;
import com.dianfei.business.flea.vo.FleaOrderVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.annotation.OperLog;
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
 * 用户端 · 跳蚤市场（Upgrade U3）：闲置发布/在售浏览/详情/下架 +
 * 积分托管下单（冻结）→ 发货 → 收货结算（转账）→ 评价，取消退冻。
 */
@Tag(name = "用户端-跳蚤市场")
@RestController
@RequestMapping("/api/flea")
@RequiredArgsConstructor
public class FleaController {

    private final FleaItemService fleaItemService;
    private final FleaOrderService fleaOrderService;

    // ---------- 商品 ----------

    @Operation(summary = "发布闲置（status=1 在售；分类枚举 + 图片地址校验）")
    @Idempotent(prefix = "flea:item")
    @OperLog(value = "发布闲置", module = "跳蚤市场")
    @PostMapping("/items")
    public Result<FleaItemVO> create(@Valid @RequestBody FleaItemCreateDTO dto) {
        return Result.success(fleaItemService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "在售商品分页（分类/关键词/价格区间过滤，创建时间倒序）")
    @GetMapping("/items")
    public Result<PageVO<FleaItemVO>> items(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long minPrice,
                                            @RequestParam(required = false) Long maxPrice) {
        return Result.success(fleaItemService.page(category, keyword, minPrice, maxPrice, pageNum, pageSize));
    }

    @Operation(summary = "我的发布分页（本人全部状态，U4 追加契约）")
    @GetMapping("/items/mine")
    public Result<PageVO<FleaItemVO>> mineItems(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(fleaItemService.minePage(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "商品详情（含卖家统计；已删/违规下架 404）")
    @GetMapping("/items/{id}")
    public Result<FleaItemVO> detail(@PathVariable Long id) {
        return Result.success(fleaItemService.detail(id));
    }

    @Operation(summary = "下架我的商品（仅本人 + 在售）")
    @OperLog(value = "下架闲置", module = "跳蚤市场")
    @PutMapping("/items/{id}/off")
    public Result<Void> off(@PathVariable Long id) {
        fleaItemService.off(id, UserContext.getUserId());
        return Result.success();
    }

    // ---------- 订单 ----------

    @Operation(summary = "下单（冻结买家积分托管，返回 orderNo + frozenPoints；余额不足 2006）")
    @Idempotent(prefix = "flea:order")
    @PostMapping("/orders")
    public Result<FleaOrderVO> order(@Valid @RequestBody FleaOrderCreateDTO dto) {
        return Result.success(fleaOrderService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "取消订单（仅买家 + 待发货：解冻退分 + 商品回在售）")
    @PutMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        fleaOrderService.cancel(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "发货（仅卖家 + 待发货）")
    @PutMapping("/orders/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        fleaOrderService.ship(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "确认收货（仅买家 + 待收货：转账结算给卖家）")
    @PutMapping("/orders/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        fleaOrderService.receive(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "评价订单（仅买家 + 已完成 + 未评过，评分 1-5）")
    @PutMapping("/orders/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody FleaReviewDTO dto) {
        fleaOrderService.review(id, UserContext.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "我买到的订单分页（status 过滤）")
    @GetMapping("/orders/buyer")
    public Result<PageVO<FleaOrderVO>> buyerOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) Integer status) {
        return Result.success(fleaOrderService.pageBuyer(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "我卖出的订单分页（status 过滤）")
    @GetMapping("/orders/seller")
    public Result<PageVO<FleaOrderVO>> sellerOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) Integer status) {
        return Result.success(fleaOrderService.pageSeller(UserContext.getUserId(), status, pageNum, pageSize));
    }
}

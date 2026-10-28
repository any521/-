package com.dianfei.business.mall.controller;

import com.dianfei.business.mall.dto.MallProductSaveDTO;
import com.dianfei.business.mall.entity.MallProduct;
import com.dianfei.business.mall.service.MallExchangeService;
import com.dianfei.business.mall.service.MallProductService;
import com.dianfei.business.mall.vo.MallExchangeVO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · 商城管理（C3）：商品 CRUD、上下架、库存调整；兑换单发货/完成
 */
@Tag(name = "后台-商城管理")
@RestController
@RequestMapping("/api/admin/mall")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminMallController {

    private final MallProductService mallProductService;
    private final MallExchangeService mallExchangeService;

    @Operation(summary = "商品分页（全量含下架）")
    @GetMapping("/products")
    public Result<PageVO<MallProduct>> products(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status) {
        return Result.success(mallProductService.pageAdmin(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "新增/编辑商品（编辑不覆盖库存，库存走调整接口）")
    @OperLog(value = "保存商城商品", module = "商城管理")
    @PostMapping("/products")
    public Result<MallProduct> saveProduct(@Valid @RequestBody MallProductSaveDTO dto) {
        return Result.success(mallProductService.saveProduct(dto));
    }

    @Operation(summary = "商品上/下架")
    @OperLog(value = "商品上下架", module = "商城管理")
    @PutMapping("/products/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam boolean onSale) {
        mallProductService.changeStatus(id, onSale);
        return Result.success();
    }

    @Operation(summary = "库存调整（delta 正=入库 负=核减，禁止负库存）")
    @OperLog(value = "调整商品库存", module = "商城管理")
    @PutMapping("/products/{id}/stock")
    public Result<Void> adjustStock(@PathVariable Long id, @RequestParam int delta) {
        mallProductService.adjustStock(id, delta);
        return Result.success();
    }

    @Operation(summary = "兑换单分页（按状态/用户/商品过滤）")
    @GetMapping("/exchanges")
    public Result<PageVO<MallExchangeVO>> exchanges(@RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) Long productId) {
        return Result.success(mallExchangeService.pageAdmin(status, userId, productId, pageNum, pageSize));
    }

    @Operation(summary = "兑换单发货（0已兑换 → 1已发货）")
    @OperLog(value = "兑换单发货", module = "商城管理")
    @PutMapping("/exchanges/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        mallExchangeService.ship(id);
        return Result.success();
    }

    @Operation(summary = "兑换单完成（1已发货 → 2已完成）")
    @OperLog(value = "兑换单完成", module = "商城管理")
    @PutMapping("/exchanges/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        mallExchangeService.complete(id);
        return Result.success();
    }
}

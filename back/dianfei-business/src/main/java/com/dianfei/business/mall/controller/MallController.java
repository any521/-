package com.dianfei.business.mall.controller;

import com.dianfei.business.mall.dto.MallExchangeDTO;
import com.dianfei.business.mall.entity.MallProduct;
import com.dianfei.business.mall.service.MallExchangeService;
import com.dianfei.business.mall.service.MallProductService;
import com.dianfei.business.mall.vo.MallExchangeVO;
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
 * 用户端 · 积分商城（C3）：上架商品浏览、积分兑换（余额不足 2006）、我的兑换单、取消
 */
@Tag(name = "用户端-积分商城")
@RestController
@RequestMapping("/api/mall")
@RequiredArgsConstructor
public class MallController {

    private final MallProductService mallProductService;
    private final MallExchangeService mallExchangeService;

    @Operation(summary = "上架商品分页（按积分价升序）")
    @GetMapping("/products")
    public Result<PageVO<MallProduct>> products(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(mallProductService.pageOnSale(pageNum, pageSize, keyword));
    }

    @Operation(summary = "积分兑换（校验余额→扣分→扣库存→生成兑换单，事务；余额不足返回 2006）")
    @Idempotent(prefix = "mall:exchange")
    @PostMapping("/exchange")
    public Result<MallExchangeVO> exchange(@Valid @RequestBody MallExchangeDTO dto) {
        return Result.success(mallExchangeService.exchange(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的兑换单分页")
    @GetMapping("/exchanges")
    public Result<PageVO<MallExchangeVO>> exchanges(@RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize,
                                                    @RequestParam(required = false) Integer status) {
        return Result.success(mallExchangeService.pageUserExchanges(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "兑换单详情（限本人）")
    @GetMapping("/exchanges/{id}")
    public Result<MallExchangeVO> exchangeDetail(@PathVariable Long id) {
        return Result.success(mallExchangeService.detail(id, UserContext.getUserId()));
    }

    @Operation(summary = "取消兑换（仅待发货；积分退回+库存返还）")
    @PutMapping("/exchanges/{id}/cancel")
    public Result<MallExchangeVO> cancel(@PathVariable Long id) {
        return Result.success(mallExchangeService.cancel(id, UserContext.getUserId()));
    }
}

package com.dianfei.ai.pricing.controller;

import com.dianfei.ai.pricing.dto.ItemPriceQueryDTO;
import com.dianfei.ai.pricing.dto.MarketSyncDTO;
import com.dianfei.ai.pricing.service.MarketPriceService;
import com.dianfei.ai.pricing.vo.ItemPriceVO;
import com.dianfei.ai.pricing.vo.PriceSyncVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 · 回收报价联网行情（B3）：地区选择（滑动窗口）+ 手动联网同步（内置 8 品类）
 * + 自定义物品联网询价 + IP 定位。与 RecycleController 的 /api/recycle/price 报价列表配合使用。
 */
@Tag(name = "用户端-回收报价联网行情")
@RestController
@RequestMapping("/api/ai/market-price")
@RequiredArgsConstructor
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    @Operation(summary = "手动联网更新内置 8 品类报价（异步提交，city 可空=IP 定位默认；后台执行，前端轮询 meta 感知完成）")
    @PostMapping("/sync")
    public Result<PriceSyncVO> sync(@Valid @RequestBody(required = false) MarketSyncDTO dto) {
        return Result.success(marketPriceService.syncAsync(dto == null ? null : dto.getCity()));
    }

    @Operation(summary = "自定义物品联网询价（如 易拉罐/纸箱，city 可空=IP 定位默认；成功后写 24h 缓存）")
    @PostMapping("/query")
    public Result<ItemPriceVO> query(@Valid @RequestBody ItemPriceQueryDTO dto) {
        return Result.success(marketPriceService.query(dto.getItem().trim(), dto.getCity()));
    }

    @Operation(summary = "IP 定位当前城市（定位按钮；失败回退配置默认，结果缓存 7 天）")
    @PostMapping("/locate")
    public Result<String> locate() {
        return Result.success(marketPriceService.locate());
    }
}

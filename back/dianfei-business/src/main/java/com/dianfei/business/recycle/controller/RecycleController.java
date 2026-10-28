package com.dianfei.business.recycle.controller;

import com.dianfei.business.recycle.dto.RecycleOrderCreateDTO;
import com.dianfei.business.recycle.dto.RecycleRescheduleDTO;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.entity.RecyclePrice;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.service.RecyclePriceService;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import com.dianfei.common.utils.RedisUtils;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端 · 可回收物回收（B3）：实时报价 / 创建预约单 / 我的订单 / 取消 / 改期（12 章契约 /api/recycle/*）
 */
@Tag(name = "用户端-可回收物回收")
@RestController
@RequestMapping("/api/recycle")
@RequiredArgsConstructor
public class RecycleController {

    /** 每日联网同步的前价快照键（与 dianfei-ai MarketPriceSyncTask 保持一致） */
    private static final String PREV_PRICE_KEY = "dianfei:price:market-prev:%s";
    private static final String SYNC_TIME_KEY = "dianfei:price:sync-time";
    private static final String CITY_KEY = "dianfei:price:city";

    private final RecycleOrderService orderService;
    private final RecyclePriceService priceService;
    private final RedisUtils redisUtils;

    @Operation(summary = "实时回收报价（生效品类，含较昨日涨跌）")
    @GetMapping("/price")
    public Result<List<RecyclePrice>> price() {
        List<RecyclePrice> prices = priceService.listActive();
        for (RecyclePrice price : prices) {
            String prev = redisUtils.get(PREV_PRICE_KEY.formatted(price.getCategory()));
            if (prev != null) {
                try {
                    price.setPrevPricePerKg(new java.math.BigDecimal(prev));
                } catch (NumberFormatException ignored) {
                    // 快照脏数据忽略，前端按无涨跌处理
                }
            }
        }
        return Result.success(prices);
    }

    @Operation(summary = "报价元信息（定位城市 + 最近同步时间，每日联网同步展示用）")
    @GetMapping("/price/meta")
    public Result<Map<String, String>> priceMeta() {
        Map<String, String> meta = new HashMap<>();
        meta.put("city", redisUtils.get(CITY_KEY));
        meta.put("lastSyncTime", redisUtils.get(SYNC_TIME_KEY));
        return Result.success(meta);
    }

    @Operation(summary = "创建回收预约单（AR 识别结果可带入品类/预估重量）")
    @Idempotent(prefix = "recycle:create")
    @PostMapping("/orders")
    public Result<RecycleOrderVO> create(@Valid @RequestBody RecycleOrderCreateDTO dto) {
        return Result.success(orderService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的回收单分页（含接单员工姓名/电话）")
    @GetMapping("/orders")
    public Result<PageVO<RecycleOrderVO>> myOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                                 @RequestParam(required = false) Integer status) {
        return Result.success(orderService.pageUserOrders(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "订单详情（含实收明细）")
    @GetMapping("/orders/{id}")
    public Result<RecycleOrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id, UserContext.getUserId()));
    }

    @Operation(summary = "取消订单（仅 0待接单/1已接单，否则 2003）")
    @PutMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        orderService.cancel(id, UserContext.getUserId(), reason);
        return Result.success();
    }

    @Operation(summary = "预约改期（仅 0待接单/1已接单，否则 2003）")
    @OperLog(value = "回收订单改期", module = "用户端-回收")
    @PostMapping("/orders/{id}/reschedule")
    public Result<Void> reschedule(@PathVariable Long id, @Valid @RequestBody RecycleRescheduleDTO dto) {
        // timeSlot 形如 '14:00-16:00'：取起始段的小时（冒号前）作为预约时刻
        int hour = Integer.parseInt(dto.getTimeSlot().split("-")[0].split(":")[0]);
        orderService.reschedule(id, UserContext.getUserId(), dto.getDate().atTime(hour, 0));
        return Result.success();
    }
}

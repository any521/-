package com.dianfei.ai.pricing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.pricing.dto.PricingApplyDTO;
import com.dianfei.ai.pricing.vo.PricingSuggestVO;
import com.dianfei.business.recycle.dto.RecyclePriceSaveDTO;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.entity.RecyclePrice;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.service.RecyclePriceService;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F5 动态定价编排（05 章 5.7 / E7）：近 7 日供需 → AI 服务 /v1/pricing 系数建议
 * （0.8-1.2 双端夹逼）→ 管理员确认后应用到 biz_recycle_price；应用前旧价快照入
 * Redis 供一键回滚（人工可干预与回滚）。AI 不可用回落基础价系数 1.0（D15）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingAiService {

    /** 回滚快照键：应用新价前的旧价（7 天有效，超期视为已接受新价） */
    private static final String PREV_PRICE_KEY = "dianfei:ai:pricing:prev:%s";
    private static final Duration PREV_PRICE_TTL = Duration.ofDays(7);

    private final AiServiceClient aiServiceClient;
    private final RecyclePriceService recyclePriceService;
    private final RecycleOrderService recycleOrderService;
    private final ConfigService configService;
    private final RedisUtils redisUtils;

    /** 定价建议：AI 系数 × 基础价，不落库（管理员确认后走 apply） */
    public PricingSuggestVO suggest(Long areaId, String category) {
        RecyclePrice base = recyclePriceService.requireActive(category);
        double supplyKg = recentSupplyKg(areaId, category);
        double demandKg = Double.parseDouble(configService.getValue(
                "ai.pricing.demand-kg." + category, String.valueOf(Math.max(supplyKg, 100))));

        AiContracts.PricingResponse resp;
        try {
            resp = aiServiceClient.pricing(AiContracts.PricingRequest.builder()
                    .areaId(areaId)
                    .category(category)
                    .basePrice(base.getPricePerKg().doubleValue())
                    .recentSupplyKg(supplyKg)
                    .recentDemandKg(demandKg)
                    .build());
        } catch (AiServiceException e) {
            log.warn("[F5] 定价建议不可用 fallback={} : {}", e.getFallback(), e.getMessage());
            resp = new AiContracts.PricingResponse(
                    1.0, 1.0, "AI 不可用，维持基础价", 0, true);
        }
        // Java 侧再夹逼一次（sys_config 护栏，防涨跌幅越界）
        double factor = clamp(resp.getPriceFactor());
        resp.setPriceFactor(factor);
        resp.setPointsFactor(clamp(resp.getPointsFactor()));

        PricingSuggestVO vo = new PricingSuggestVO();
        vo.setCategory(category);
        vo.setBasePrice(base.getPricePerKg());
        vo.setSuggest(resp);
        vo.setSuggestedPrice(applyFactor(base.getPricePerKg(), factor));
        vo.setRecentSupplyKg(supplyKg);
        vo.setRecentDemandKg(demandKg);
        return vo;
    }

    /** 应用系数：旧价快照入 Redis → 新价 = 基础价 × 夹逼后系数（立即生效） */
    @Transactional(rollbackFor = Exception.class)
    public RecyclePrice apply(PricingApplyDTO dto) {
        RecyclePrice base = recyclePriceService.requireActive(dto.getCategory());
        double factor = clamp(dto.getPriceFactor());
        redisUtils.set(PREV_PRICE_KEY.formatted(dto.getCategory()),
                base.getPricePerKg().toPlainString(), PREV_PRICE_TTL);

        RecyclePriceSaveDTO save = new RecyclePriceSaveDTO();
        save.setCategory(dto.getCategory());
        save.setPricePerKg(applyFactor(base.getPricePerKg(), factor));
        save.setStatus(1);
        RecyclePrice updated = recyclePriceService.saveByCategory(save);
        log.info("[F5] 应用定价 category={} {} -> {}（factor={}）",
                dto.getCategory(), base.getPricePerKg(), updated.getPricePerKg(), factor);
        return updated;
    }

    /** 一键回滚：恢复应用前快照价（无快照报错提示） */
    @Transactional(rollbackFor = Exception.class)
    public RecyclePrice rollback(String category) {
        String prev = redisUtils.get(PREV_PRICE_KEY.formatted(category));
        if (prev == null) {
            throw new BusinessException("无可回滚的历史报价（快照已过期或未应用过 AI 定价）");
        }
        RecyclePriceSaveDTO save = new RecyclePriceSaveDTO();
        save.setCategory(category);
        save.setPricePerKg(new BigDecimal(prev));
        save.setStatus(1);
        RecyclePrice restored = recyclePriceService.saveByCategory(save);
        redisUtils.delete(PREV_PRICE_KEY.formatted(category));
        log.info("[F5] 回滚定价 category={} -> {}", category, prev);
        return restored;
    }

    /** 近 7 日该片区该品类完成单实收量（无实收回退预估） */
    private double recentSupplyKg(Long areaId, String category) {
        List<RecycleOrder> orders = recycleOrderService.list(new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getAreaId, areaId)
                .eq(RecycleOrder::getCategory, category)
                .eq(RecycleOrder::getStatus, OrderStatusEnum.COMPLETED.getCode())
                .ge(RecycleOrder::getFinishTime, LocalDateTime.now().minusDays(7)));
        return orders.stream()
                .map(o -> o.getWeight() != null ? o.getWeight()
                        : o.getEstWeight() != null ? o.getEstWeight() : BigDecimal.ZERO)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
    }

    /** 涨跌幅护栏（sys_config 可调）：默认 0.8-1.2 */
    private double clamp(Double factor) {
        double min = Double.parseDouble(configService.getValue("ai.pricing.factor-min", "0.8"));
        double max = Double.parseDouble(configService.getValue("ai.pricing.factor-max", "1.2"));
        return Math.min(Math.max(factor == null ? 1.0 : factor, min), max);
    }

    private static BigDecimal applyFactor(BigDecimal base, double factor) {
        return base.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }
}

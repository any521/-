package com.dianfei.ai.pricing.vo;

import com.dianfei.ai.client.dto.AiContracts;
import lombok.Data;

import java.math.BigDecimal;

/** F5 定价建议返回：AI 系数建议 + 按系数折算后的建议价（未落库，确认后 apply） */
@Data
public class PricingSuggestVO {

    private String category;

    /** 当前生效基础价（元/kg） */
    private BigDecimal basePrice;

    /** AI 建议（系数已在 Java 侧夹逼；degraded=true 表示 AI 不可用回落 1.0） */
    private AiContracts.PricingResponse suggest;

    /** 建议价 = 基础价 × 夹逼后系数（保留 2 位） */
    private BigDecimal suggestedPrice;

    /** 近 7 日该片区该品类完成单实收量（kg） */
    private Double recentSupplyKg;

    /** 需求量（sys_config 可配，缺省取 max(供给,100)） */
    private Double recentDemandKg;
}

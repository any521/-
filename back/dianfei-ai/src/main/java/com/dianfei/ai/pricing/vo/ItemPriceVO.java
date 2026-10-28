package com.dianfei.ai.pricing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 自定义物品联网询价结果（POST /api/ai/market-price/query，不落库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPriceVO {

    /** 查询物品（中文，如 易拉罐/纸箱） */
    private String item;

    /** 查询城市（IP 定位，失败回退配置默认） */
    private String city;

    /** 当日报价（元/公斤）；null=联网未检索到 */
    private BigDecimal pricePerKg;

    private String unit;

    /** 定价依据/来源说明 */
    private String note;

    /** 命中来源标题 */
    private String source;

    /** 降级标记（正则/mock 提取，非 LLM 提炼） */
    private Boolean degraded;

    /** 是否来自缓存（页面加载/重复读取，非本次联网查询） */
    private Boolean cached;
}

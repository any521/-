package com.dianfei.business.flea.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 卖家统计 VO（Upgrade U3）：商品详情页卖家维度统计（在售数/已售出数）。
 */
@Data
@Builder
public class FleaStatsVO {

    /** 在售数（status=1） */
    private Long onSaleCount;

    /** 已售出数（status=2） */
    private Long soldCount;
}

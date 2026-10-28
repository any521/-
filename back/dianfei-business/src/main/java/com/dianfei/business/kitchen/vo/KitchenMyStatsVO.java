package com.dianfei.business.kitchen.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户个人垃圾投放累计（B4）：总上报次数/总袋数/今日袋数/待审核笔数（审核通过才发分）
 */
@Data
@Builder
public class KitchenMyStatsVO {

    private Long totalReports;

    private Long totalBags;

    private Long todayBags;

    /** 待审核笔数（审核通过才发分；字段名兼容旧契约） */
    private Long frozenReports;
}

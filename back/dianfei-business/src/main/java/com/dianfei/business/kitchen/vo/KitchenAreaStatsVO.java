package com.dianfei.business.kitchen.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 片区垃圾投放看板（B4，GET /api/kitchen/stats/area）：当日袋数与清运阈值对比
 */
@Data
@Builder
public class KitchenAreaStatsVO {

    private String areaId;

    private String areaName;

    /** 当日已上报袋数 */
    private Long todayBags;

    /** 当日上报笔数 */
    private Long todayReports;

    /** 清运阈值（袋），达到即生成次日清运任务 */
    private Integer cleanThreshold;

    /** 是否已达清运阈值 */
    private Boolean thresholdReached;
}

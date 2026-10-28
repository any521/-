package com.dianfei.business.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度报表行（C5 大屏）：四域月度聚合合并（回收/垃圾投放/维修/碳减排）
 */
@Data
@Schema(description = "月度报表行")
public class MonthlyReportVO {

    @Schema(description = "月份 yyyy-MM")
    private String month;

    @Schema(description = "完成回收单数")
    private Long recycleOrders = 0L;

    @Schema(description = "回收总重量 kg")
    private BigDecimal recycleWeight = BigDecimal.ZERO;

    @Schema(description = "垃圾投放总袋数")
    private Long kitchenBags = 0L;

    @Schema(description = "维修单数")
    private Long repairOrders = 0L;

    @Schema(description = "碳减排量 kgCO2")
    private BigDecimal carbonReduced = BigDecimal.ZERO;
}

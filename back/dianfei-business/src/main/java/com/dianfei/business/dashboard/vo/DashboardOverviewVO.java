package com.dianfei.business.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据大屏总览（C5，GET /api/admin/dashboard/overview）：
 * 回收量/垃圾投放量/维修单数/碳减排测算/片区排行/月度报表，MVP 单库聚合 + 1s 内存缓存
 */
@Data
@Schema(description = "数据大屏总览")
public class DashboardOverviewVO {

    @Schema(description = "回收订单总数")
    private Long recycleTotalOrders;

    @Schema(description = "已完成回收单数")
    private Long recycleFinishedOrders;

    @Schema(description = "回收总重量 kg（完成单实收）")
    private BigDecimal recycleTotalWeight;

    @Schema(description = "今日新增回收单")
    private Long recycleTodayOrders;

    @Schema(description = "垃圾投放总袋数")
    private Long kitchenTotalBags;

    @Schema(description = "今日垃圾投放袋数")
    private Long kitchenTodayBags;

    @Schema(description = "维修单总数")
    private Long repairTotalOrders;

    @Schema(description = "维修完成单数（可修复闭环）")
    private Long repairFinishedOrders;

    @Schema(description = "维修转回收单数（平台特色联动）")
    private Long repairTransferredOrders;

    @Schema(description = "累计碳减排测算 kgCO2（可信账本口径）")
    private BigDecimal carbonReducedTotal;

    @Schema(description = "片区排行（按回收重量降序）")
    private List<AreaRankVO> areaRanking;

    @Schema(description = "近 12 个月月度报表")
    private List<MonthlyReportVO> monthlyReport;

    @Schema(description = "快照生成时刻（1s 缓存窗口内返回同一快照）")
    private LocalDateTime generatedAt;
}

package com.dianfei.business.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工作台摘要（G6，GET /api/admin/dashboard/summary）：
 * 今日单量/重量/碳减排/用户数 + 近 7 日趋势 + 品类占比 + 片区排名，
 * 聚合口径与 overview 对齐（回收按 create_time、重量按 finish_time、碳按账本流水）。
 */
@Data
@Schema(description = "工作台摘要")
public class DashboardSummaryVO {

    @Schema(description = "今日单量（回收未取消 + 垃圾投放）")
    private Integer todayOrders;

    @Schema(description = "今日回收重量 kg（完成单实收）")
    private BigDecimal todayWeight;

    @Schema(description = "今日碳减排 kgCO2（账本流水；无流水按回收重量 × RECYCLE 规则折算）")
    private BigDecimal todayCarbon;

    @Schema(description = "平台用户总数（deleted=0）")
    private Long totalUsers;

    @Schema(description = "近 7 日趋势（缺失日补 0）")
    private List<DayStat> weekTrend;

    @Schema(description = "回收品类占比 Top5（字典 label）")
    private List<NameValue> categoryRatio;

    @Schema(description = "片区回收重量排名（复用大屏 areaRanking）")
    private List<AreaRankItem> areaRank;

    @Data
    @Schema(description = "单日趋势")
    public static class DayStat {

        @Schema(description = "日期 MM-dd")
        private String date;

        @Schema(description = "回收单数（当日下单，排除取消）")
        private Long orders;

        @Schema(description = "完成重量 kg")
        private BigDecimal weight;

        @Schema(description = "碳减排 kgCO2")
        private BigDecimal carbon;
    }

    @Data
    @Schema(description = "名称-数值对")
    public static class NameValue {

        private String name;

        private Integer value;
    }

    @Data
    @Schema(description = "片区排名行（对齐前端 areaRank[{name,weight}]）")
    public static class AreaRankItem {

        private String name;

        private BigDecimal weight;
    }
}

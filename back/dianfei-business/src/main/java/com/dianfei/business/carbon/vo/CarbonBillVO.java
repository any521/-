package com.dianfei.business.carbon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 低碳账单（GET /api/carbon/bill）：按月聚合积分与减排
 */
@Data
@Builder
@Schema(description = "低碳账单")
public class CarbonBillVO {

    @Schema(description = "账单月份 yyyy-MM")
    private String month;

    @Schema(description = "本月获得积分（含解冻）")
    private long totalGained;

    @Schema(description = "本月消费/扣减积分（正数）")
    private long totalSpent;

    @Schema(description = "本月减排量 kgCO2")
    private BigDecimal totalCarbonReduced;

    /** 前端契约字段：与 totalCarbonReduced 同值 */
    @Schema(description = "本月减排量 kgCO2（前端契约字段）")
    private BigDecimal totalCarbon;

    @Schema(description = "本月获得积分合计（type=1）")
    private long totalPoints;

    @Schema(description = "按日减排曲线（缺失日补 0，按日升序）")
    private List<DayCarbon> days;

    @Schema(description = "相当于植树棵数 = totalCarbon / 18")
    private BigDecimal treeEquivalent;

    @Schema(description = "按业务类型明细")
    private List<Item> items;

    @Data
    @Builder
    @Schema(description = "单日减排")
    public static class DayCarbon {

        @Schema(description = "日期 yyyy-MM-dd")
        private String date;

        @Schema(description = "当日减排量 kgCO2")
        private BigDecimal carbon;
    }

    @Data
    @Builder
    @Schema(description = "账单分项")
    public static class Item {

        @Schema(description = "业务类型 RECYCLE/KITCHEN/REPAIR/MALL")
        private String orderType;

        @Schema(description = "笔数")
        private long count;

        @Schema(description = "积分合计（正入负出）")
        private long points;

        @Schema(description = "减排量合计 kgCO2")
        private BigDecimal carbonReduced;
    }
}

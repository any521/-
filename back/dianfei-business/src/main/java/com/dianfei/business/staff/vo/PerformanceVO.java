package com.dianfei.business.staff.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工月度绩效（GET /api/staff/performance）
 */
@Data
public class PerformanceVO {

    /** 统计月份 yyyy-MM */
    private String month;

    /** 完成单数（回收完成 + 维修完成 + 厨余任务完成） */
    private Long orders;

    /** 核验总重 kg（回收实收 + 厨余归档） */
    private BigDecimal weight;

    /** 经手金额 元（回收核算） */
    private BigDecimal amount;

    /** 带动碳减排 kgCO₂（回收单回查碳流水） */
    private BigDecimal carbon;

    /** 同片区当月单数降序排名（1 起；无他人=1） */
    private Integer rank;

    /** 好评率 %（暂无评价数据源，恒 0，前端显示「—」） */
    private BigDecimal praiseRate;

    /** 近 7 日（含今日）按日完成单数，缺失日补 0 */
    private List<DayStat> days;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayStat {
        /** yyyy-MM-dd */
        private String date;
        private Long orders;
    }
}

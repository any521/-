package com.dianfei.business.repair.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 技术员提交检修报告入参（C1）：可修复性判定 + 配件 + 科普留痕。
 */
@Data
public class RepairReportDTO {

    /** 可修复性判定 1可修复 2不可修复 */
    @NotNull(message = "可修复性判定不能为空")
    private Integer repairable;

    /** 检修报告结构化内容（故障定位/处理过程等） */
    private String reportContent;

    /** 科普留痕（面向用户的用电安全讲解摘要） */
    private String scienceNote;

    /** 更换配件明细 */
    @Valid
    private List<PartItem> parts;

    @Data
    public static class PartItem {
        @NotNull(message = "配件名称不能为空")
        private String partName;
        private String partModel;
        @Positive(message = "配件数量必须大于 0")
        @Max(value = 10000, message = "配件数量不能超过 10000")
        private Integer quantity = 1;
        @PositiveOrZero(message = "配件价格不能为负数")
        @DecimalMax(value = "1000000", message = "配件价格不能超过 1000000")
        private BigDecimal price = BigDecimal.ZERO;
    }
}

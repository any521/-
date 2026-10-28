package com.dianfei.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * F4 派单求解入参（12 章 POST /api/dispatch/plan）
 */
@Data
@Schema(description = "VRPTW 派单求解请求")
public class DispatchPlanDTO {

    @NotNull(message = "计划日期不能为空")
    @Schema(description = "计划日期（当日待派单）", example = "2026-08-01")
    private LocalDate date;

    @Schema(description = "片区 ID，空则全部片区")
    private Long areaId;

    @Schema(description = "true=求解后直接指派并通知员工；false=仅预览方案", example = "false")
    private Boolean apply = false;
}

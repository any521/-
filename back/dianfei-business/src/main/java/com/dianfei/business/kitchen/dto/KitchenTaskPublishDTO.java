package com.dianfei.business.kitchen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 清运任务发布入参（POST /api/admin/kitchen/tasks/publish，B4）：
 * 管理员指定片区/清运日期/袋数直接发布，替代纯自动聚合兜底；uk_task_area_date 幂等拦截重复发布
 */
@Data
@Schema(description = "清运任务发布入参")
public class KitchenTaskPublishDTO {

    @Schema(description = "片区 ID")
    @NotNull(message = "请选择片区")
    private Long areaId;

    @Schema(description = "清运日期（不早于今天）")
    @NotNull(message = "请选择清运日期")
    private LocalDate taskDate;

    @Schema(description = "清运袋数")
    @NotNull(message = "请输入清运袋数")
    @Min(value = 1, message = "清运袋数至少 1 袋")
    private Integer totalBags;
}

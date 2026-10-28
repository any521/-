package com.dianfei.business.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车辆设备运维登记入参（C4）
 */
@Data
public class MaintenanceSaveDTO {

    @Schema(description = "1清运车辆 2设备")
    @NotNull(message = "运维对象类型不能为空")
    private Integer targetType;

    @Schema(description = "车牌号/设备编号")
    @NotBlank(message = "车牌号/设备编号不能为空")
    private String targetNo;

    @NotBlank(message = "运维内容不能为空")
    @Size(max = 500, message = "运维内容不超过 500 字")
    private String content;

    @Schema(description = "费用（元）")
    private BigDecimal cost;

    @NotNull(message = "运维日期不能为空")
    private LocalDate maintDate;
}

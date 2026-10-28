package com.dianfei.business.kitchen.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台车辆配置入参（B4）：vehicle_no 唯一 upsert
 */
@Data
public class VehicleSaveDTO {

    private Long id;

    @NotBlank(message = "车牌号不能为空")
    private String vehicleNo;

    /** 常驻片区（可空=机动车辆） */
    private Long areaId;

    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量至少为 1 袋")
    private Integer capacity;

    /** 0停用 1可用 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}

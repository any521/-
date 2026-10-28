package com.dianfei.business.kitchen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 环卫员清运归档入参（B4）：总重量/车号/终点/凭证，归档后触发当日上报统一发分
 */
@Data
public class KitchenTaskFinishDTO {

    @NotNull(message = "总重量不能为空")
    @DecimalMin(value = "0.01", message = "总重量必须大于 0")
    private java.math.BigDecimal totalWeight;

    @NotBlank(message = "车号不能为空")
    private String vehicleNo;

    @NotBlank(message = "终点不能为空")
    private String destination;

    /** 凭证照片（MinIO，可空） */
    private String proofUrl;
}

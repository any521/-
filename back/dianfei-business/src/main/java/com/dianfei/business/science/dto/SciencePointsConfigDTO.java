package com.dianfei.business.science.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 积分配置 DTO（V42）
 */
@Data
public class SciencePointsConfigDTO {
    @NotNull(message = "单次下载积分不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "单次下载积分必须大于 0")
    private Integer downloadPoints;

    @DecimalMin(value = "0.01", inclusive = false, message = "基础积分单价必须大于 0.01")
    private Double basePoints;

    @DecimalMin(value = "0.1", inclusive = true, message = "热门系数最小为 0.1")
    private Double multiplier;
}

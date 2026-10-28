package com.dianfei.business.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 片区新增/修改入参
 */
@Data
@Schema(description = "片区新增/修改入参")
public class AreaSaveDTO {

    @Schema(description = "片区 ID（修改时必传）")
    private Long id;

    @Schema(description = "片区名称")
    @NotBlank(message = "片区名称不能为空")
    private String areaName;

    @Schema(description = "片区地址")
    private String address;

    @Schema(description = "片区中心经度（驾驶舱地图标注）")
    private BigDecimal lng;

    @Schema(description = "片区中心纬度")
    private BigDecimal lat;

    @Schema(description = "厨余清运阈值（袋）")
    @Min(value = 1, message = "清运阈值最小为 1")
    private Integer cleanThreshold;

    @Schema(description = "片区户数（风控户均基数）")
    @Min(value = 0, message = "户数不能为负")
    private Integer householdCount;

    @Schema(description = "状态：0停用 1启用")
    private Integer status;
}

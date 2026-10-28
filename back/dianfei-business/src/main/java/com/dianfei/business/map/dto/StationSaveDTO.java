package com.dianfei.business.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 垃圾站点新增/编辑 DTO（管理端，AddressPicker 地图选点回填 lng/lat/address）
 */
@Data
public class StationSaveDTO {

    private Long id;

    @NotBlank(message = "站点名称必填")
    private String name;

    /** RECYCLE 回收站 / KITCHEN 垃圾站 / COLLECT 集散点 */
    @NotBlank(message = "站点类型必填")
    private String stationType;

    @NotNull(message = "经度必填（地图选点）")
    private BigDecimal lng;

    @NotNull(message = "纬度必填（地图选点）")
    private BigDecimal lat;

    private String address;

    private String openHours;

    private Integer status;
}

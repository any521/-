package com.dianfei.business.kitchen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户端垃圾投放上报入参（B4）：服务端校验投放时段 + 距垃圾回收站 50m 内，
 * 一律转工作人员审核，审核通过才发积分。
 */
@Data
public class KitchenReportCreateDTO {

    /** 所属片区（可空：缺省由后端按经纬度自动创建/匹配片区，精确到村） */
    private Long areaId;

    @NotBlank(message = "投放点不能为空")
    private String pointName;

    @NotNull(message = "袋数不能为空")
    @Min(value = 1, message = "袋数至少为 1")
    @Max(value = 999, message = "袋数超出合理范围")
    private Integer bagCount;

    /** 投放照片（MinIO，可空；F1 视觉审核接入点） */
    private String photoUrl;

    /** 投放经度（必填：50m 站点校验 + 片区自动匹配依据） */
    @NotNull(message = "投放定位不能为空，请开启定位后重试")
    private BigDecimal lng;

    /** 投放纬度 */
    @NotNull(message = "投放定位不能为空，请开启定位后重试")
    private BigDecimal lat;
}

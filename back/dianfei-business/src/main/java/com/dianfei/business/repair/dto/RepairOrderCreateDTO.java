package com.dianfei.business.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端创建维修工单入参（C1）。
 */
@Data
public class RepairOrderCreateDTO {

    @NotBlank(message = "故障类型不能为空")
    private String faultType;

    /** 故障描述（AI 预诊输入，可空） */
    private String faultDesc;

    @NotBlank(message = "上门地址不能为空")
    private String address;

    private BigDecimal lng;
    private BigDecimal lat;

    /** 所属片区（可空：缺省由后端按经纬度自动创建/匹配片区，精确到村） */
    private Long areaId;

    @NotNull(message = "上门时段起不能为空")
    private LocalDateTime appointStart;

    @NotNull(message = "上门时段止不能为空")
    private LocalDateTime appointEnd;

    /** 以旧换新意向 0否 1是 */
    private Integer oldForNew = 0;

    /** 天然气配送顺带 0否 1是 */
    private Integer gasDelivery = 0;

    /** 用户承诺服务费积分（onsite02：可空=不使用积分支付；>0 时校验档位+余额） */
    private Long servicePoints;
}

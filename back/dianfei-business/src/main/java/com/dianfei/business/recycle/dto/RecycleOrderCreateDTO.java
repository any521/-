package com.dianfei.business.recycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端创建回收预约单入参（B3）：AR 识别结果直接带入 category/estWeight（11.1）
 */
@Data
public class RecycleOrderCreateDTO {

    @NotBlank(message = "品类不能为空")
    private String category;

    /** 用户预估重量 kg（AR 识别可带入，可空） */
    @Positive(message = "预估重量必须大于 0")
    @DecimalMax(value = "10000", message = "预估重量不能超过 10000kg")
    private BigDecimal estWeight;

    /** 件数（可空） */
    @Positive(message = "件数必须大于 0")
    @jakarta.validation.constraints.Max(value = 10000, message = "件数不能超过 10000")
    private Integer quantity;

    @NotBlank(message = "上门地址不能为空")
    private String address;

    private BigDecimal lng;
    private BigDecimal lat;

    /** 所属片区（可空：缺省由后端按经纬度自动创建/匹配片区，精确到村） */
    private Long areaId;

    @NotNull(message = "预约上门时间不能为空")
    private LocalDateTime appointTime;

    /** 用户承诺服务费积分（onsite02：可空=不使用积分支付；>0 时校验档位+余额） */
    private Long servicePoints;
}

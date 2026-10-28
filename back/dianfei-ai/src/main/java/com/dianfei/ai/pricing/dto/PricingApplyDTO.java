package com.dianfei.ai.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** F5 定价应用请求：管理员确认建议系数后应用到 biz_recycle_price */
@Data
public class PricingApplyDTO {

    @NotBlank(message = "品类不能为空")
    private String category;

    /** 应用的价格系数（服务端仍按 sys_config 护栏夹逼 0.8-1.2） */
    @NotNull(message = "价格系数不能为空")
    private Double priceFactor;
}

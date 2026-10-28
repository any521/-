package com.dianfei.business.recycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 后台报价配置入参（B3）：category 幂等 upsert（零代码新增品类）
 */
@Data
public class RecyclePriceSaveDTO {

    @NotBlank(message = "品类不能为空")
    private String category;

    @NotNull(message = "报价不能为空")
    private BigDecimal pricePerKg;

    /** 0下架 1生效，缺省 1 */
    private Integer status;

    /** 联网行情地区（可空；仅行情同步写入，后台手工配置不传时保留原值） */
    private String city;
}

package com.dianfei.ai.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自定义物品联网询价入参
 */
@Data
public class ItemPriceQueryDTO {

    @Schema(description = "查询物品（中文，如 易拉罐/纸箱）")
    @NotBlank(message = "查询物品不能为空")
    @Size(max = 32, message = "查询物品最长 32 字")
    private String item;

    @Schema(description = "查询城市（如 北京/上海；空=IP 定位默认）")
    @Size(max = 32, message = "城市最长 32 字")
    private String city;
}

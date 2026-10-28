package com.dianfei.business.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 兑换下单（用户端 C3）
 */
@Data
@Schema(description = "积分兑换下单")
public class MallExchangeDTO {

    @NotNull(message = "商品不能为空")
    @Schema(description = "商品 ID")
    private Long productId;

    @Min(value = 1, message = "兑换数量至少为 1")
    @Schema(description = "兑换数量（默认 1）")
    private Integer quantity;

    @Schema(description = "收货地址（实物商品必填）")
    private String address;
}

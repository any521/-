package com.dianfei.business.flea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 跳蚤下单（Upgrade U3）：itemId + 收货地址；余额/在售/非本人商品校验在 Service。
 */
@Data
public class FleaOrderCreateDTO {

    @NotNull(message = "商品不能为空")
    private Long itemId;

    @NotBlank(message = "收货地址不能为空")
    @Size(max = 255, message = "收货地址最长 255")
    private String address;
}

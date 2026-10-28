package com.dianfei.business.recycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工异常上报入参（B3）：订单转状态 5 异常并通知后台复核
 */
@Data
public class RecycleExceptionDTO {

    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    @NotBlank(message = "异常原因不能为空")
    private String reason;
}

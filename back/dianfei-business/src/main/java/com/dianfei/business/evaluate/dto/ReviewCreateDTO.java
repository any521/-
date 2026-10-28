package com.dianfei.business.evaluate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交订单评价入参（GapFill B2）
 */
@Data
@Schema(description = "订单评价提交参数")
public class ReviewCreateDTO {

    @Schema(description = "订单类型：RECYCLE/KITCHEN/REPAIR")
    @NotBlank(message = "订单类型不能为空")
    private String orderType;

    @Schema(description = "业务订单 ID")
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    @Schema(description = "星级 1-5")
    @NotNull(message = "星级不能为空")
    @Min(value = 1, message = "星级范围 1-5")
    @Max(value = 5, message = "星级范围 1-5")
    private Integer rating;

    @Schema(description = "评价内容（可选）")
    @Size(max = 500, message = "评价内容最长 500 字")
    private String comment;
}

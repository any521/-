package com.dianfei.business.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交售后入参（C4）
 */
@Data
@Schema(description = "售后提交参数")
public class ComplaintCreateDTO {

    @Schema(description = "来源单类型：RECYCLE/KITCHEN/REPAIR/MALL")
    @NotBlank(message = "来源单类型不能为空")
    private String orderType;

    @Schema(description = "来源单 ID")
    @NotNull(message = "来源单 ID 不能为空")
    private Long orderId;

    @Schema(description = "1投诉 2退款 3追责 4补发积分 5纠纷仲裁")
    @NotNull(message = "售后类型不能为空")
    @Min(value = 1, message = "售后类型不合法")
    @Max(value = 5, message = "售后类型不合法")
    private Integer type;

    @Schema(description = "诉求描述")
    @NotBlank(message = "诉求描述不能为空")
    @Size(max = 500, message = "诉求描述最长 500 字")
    private String content;
}

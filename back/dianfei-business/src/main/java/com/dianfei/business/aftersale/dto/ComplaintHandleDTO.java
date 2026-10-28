package com.dianfei.business.aftersale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台售后处理入参（C4）：受理（可选补发/追责扣分）或驳回
 */
@Data
@Schema(description = "售后处理参数")
public class ComplaintHandleDTO {

    @Schema(description = "true 受理并结案，false 驳回")
    @NotNull(message = "处理结论不能为空")
    private Boolean approved;

    @Schema(description = "处理结论说明（退款金额/仲裁结果等）")
    @Size(max = 500, message = "处理结论最长 500 字")
    private String handleResult;

    @Schema(description = "补发积分数（受理时可选，走账本 REISSUE 流水）")
    @Positive(message = "补发积分必须为正数")
    private Long compensatePoints;

    @Schema(description = "追责扣除积分数（受理时可选，走账本 DEDUCT 流水）")
    @Positive(message = "扣除积分必须为正数")
    private Long deductPoints;
}

package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端实名审核入参（GapFill B1，V1）：pass 通过/驳回 + 备注（驳回时建议必填）
 */
@Data
@Schema(description = "实名审核参数")
public class RealNameAuditDTO {

    @Schema(description = "true 通过 / false 驳回")
    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    @Schema(description = "审核备注（驳回原因）")
    @Size(max = 255, message = "审核备注最长 255 字")
    private String remark;
}

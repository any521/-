package com.dianfei.business.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 请假审核入参（C4）：通过后请假区间内已有排班自动移除
 */
@Data
public class LeaveAuditDTO {

    @Schema(description = "true 通过 false 驳回")
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;

    @Size(max = 255, message = "审核备注不超过 255 字")
    private String auditRemark;
}

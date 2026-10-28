package com.dianfei.business.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工审核入参
 */
@Data
@Schema(description = "员工审核入参")
public class StaffAuditDTO {

    @Schema(description = "是否通过：true通过上岗 false驳回")
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;

    @Schema(description = "审核备注")
    private String remark;
}

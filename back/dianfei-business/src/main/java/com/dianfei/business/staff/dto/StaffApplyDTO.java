package com.dianfei.business.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工作人员自助申请入参（POST /api/staff/apply，G1：回收员/清运工/维修技术员三类均可申请）
 */
@Data
@Schema(description = "工作人员自助申请入参")
public class StaffApplyDTO {

    @Schema(description = "员工类型：1回收员 2清运工（环卫员） 3维修技术员")
    @NotNull(message = "请选择申请工种")
    private Integer staffType;

    @Schema(description = "姓名")
    @NotBlank(message = "姓名不能为空")
    private String staffName;

    @Schema(description = "联系电话")
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @Schema(description = "片区 ID（可空=待审核通过后由平台分配）")
    private Long areaId;

    @Schema(description = "申请备注（可空）")
    private String reason;
}

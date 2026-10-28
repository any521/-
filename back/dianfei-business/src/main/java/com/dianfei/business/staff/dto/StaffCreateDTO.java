package com.dianfei.business.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工新增入参（新增即进入待审核，审核通过后上岗）
 */
@Data
@Schema(description = "员工新增入参")
public class StaffCreateDTO {

    @Schema(description = "关联系统用户 ID（sys_user）")
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @Schema(description = "员工姓名")
    @NotBlank(message = "员工姓名不能为空")
    private String staffName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "员工类型：1回收员 2环卫员 3技术员")
    @NotNull(message = "员工类型不能为空")
    private Integer staffType;

    @Schema(description = "绑定片区 ID")
    @NotNull(message = "片区不能为空")
    private Long areaId;
}

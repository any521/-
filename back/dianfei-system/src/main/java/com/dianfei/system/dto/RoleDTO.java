package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 角色新增/编辑入参
 */
@Data
@Schema(description = "角色新增/编辑参数")
public class RoleDTO {

    @Schema(description = "角色 id，编辑时必传")
    private Long id;

    @Schema(description = "角色编码，如 ADMIN")
    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "角色编码须为大写字母/数字/下划线，2-32 位")
    private String roleCode;

    @Schema(description = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "0 停用 / 1 启用")
    private Integer status;
}

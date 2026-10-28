package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统用户新增/编辑（管理端，G1）：id 空=新增、非空=编辑（前端只有 POST，无 PUT）
 */
@Data
@Schema(description = "系统用户新增/编辑")
public class AdminUserSaveDTO {

    @Schema(description = "用户ID，空=新增，非空=编辑")
    private Long id;

    @Schema(description = "用户名（唯一）", example = "zhangsan")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色编码，缺省 USER", example = "USER")
    private String role;

    @Schema(description = "密码，新增缺省 123456；编辑留空不改密")
    private String password;

    @Schema(description = "状态 0停用/1正常，缺省 1", example = "1")
    private Integer status;
}

package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "设置密码入参")
public class SetPasswordDTO {

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String newPassword;
}

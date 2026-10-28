package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮箱验证码登录入参（验证码即身份证明：未注册自动建号，已注册直接登录，
 * 替代"先 register 再派生密码 login"的桥接方案，避免密码不一致累计锁定）
 */
@Data
@Schema(description = "邮箱验证码登录入参")
public class LoginByCodeDTO {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}

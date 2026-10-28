package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交实名认证入参（GapFill B1，V1）：姓名 + 身份证（18 位格式校验）
 */
@Data
@Schema(description = "实名认证提交参数")
public class RealNameSubmitDTO {

    @Schema(description = "真实姓名")
    @NotBlank(message = "真实姓名不能为空")
    @Size(min = 2, max = 20, message = "真实姓名长度 2-20 字")
    private String realName;

    @Schema(description = "身份证号（18 位）")
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;
}

package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户显示昵称更新入参（n7）：昵称去空白在 Controller 统一 trim
 */
@Data
@Schema(description = "昵称更新参数")
public class NicknameUpdateDTO {

    @Schema(description = "显示昵称")
    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 30, message = "昵称长度需在 1-30 字符之间")
    private String nickname;
}

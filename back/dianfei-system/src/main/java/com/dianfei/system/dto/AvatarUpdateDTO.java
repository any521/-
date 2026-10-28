package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户头像更新入参（Upgrade U1）：avatarUrl 格式合法性在 Controller 校验
 */
@Data
@Schema(description = "头像更新参数")
public class AvatarUpdateDTO {

    @Schema(description = "头像图片 URL")
    @NotBlank(message = "头像不能为空")
    @Size(max = 500, message = "头像 URL 长度不能超过 500")
    private String avatarUrl;
}

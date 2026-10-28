package com.dianfei.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "双令牌返回")
public class TokenVO {

    @Schema(description = "访问令牌，Header: Authorization: Bearer <accessToken>")
    private String accessToken;

    @Schema(description = "刷新令牌，仅用于 /api/auth/refresh")
    private String refreshToken;

    @Schema(description = "accessToken 有效期（秒）")
    private Long expiresIn;
}

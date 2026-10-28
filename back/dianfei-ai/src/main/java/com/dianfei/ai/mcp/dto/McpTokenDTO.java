package com.dianfei.ai.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth2 令牌换取请求（授权码模式）
 */
@Data
@Schema(description = "MCP OAuth2 令牌请求")
public class McpTokenDTO {

    @NotBlank(message = "grant_type 不能为空")
    @Schema(description = "固定 authorization_code")
    private String grantType;

    @NotBlank(message = "client_id 不能为空")
    private String clientId;

    @NotBlank(message = "client_secret 不能为空")
    private String clientSecret;

    @NotBlank(message = "code 不能为空")
    @Schema(description = "授权码（5 分钟单次有效）")
    private String code;

    @NotBlank(message = "redirect_uri 不能为空")
    @Schema(description = "必须与授权时一致")
    private String redirectUri;
}

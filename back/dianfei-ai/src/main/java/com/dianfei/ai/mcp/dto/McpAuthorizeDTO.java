package com.dianfei.ai.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth2 授权请求（用户已登录，同意向第三方客户端授予作用域）
 */
@Data
@Schema(description = "MCP OAuth2 授权请求")
public class McpAuthorizeDTO {

    @NotBlank(message = "client_id 不能为空")
    @Schema(description = "第三方客户端 ID")
    private String clientId;

    @NotBlank(message = "redirect_uri 不能为空")
    @Schema(description = "回调地址（必须在客户端注册列表内）")
    private String redirectUri;

    @NotBlank(message = "scope 不能为空")
    @Schema(description = "请求作用域，逗号分隔：read / write")
    private String scope;

    @Schema(description = "客户端防 CSRF 状态值，原样回传")
    private String state;
}

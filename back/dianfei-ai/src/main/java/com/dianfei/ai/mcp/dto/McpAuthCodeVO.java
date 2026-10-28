package com.dianfei.ai.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OAuth2 授权码响应：前端携 code+state 重定向回第三方 redirect_uri
 */
@Data
@Builder
@Schema(description = "MCP OAuth2 授权码")
public class McpAuthCodeVO {

    private String code;

    @Schema(description = "原样回传的客户端状态值")
    private String state;

    private String redirectUri;
}

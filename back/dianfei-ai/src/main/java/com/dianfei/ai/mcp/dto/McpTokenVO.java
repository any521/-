package com.dianfei.ai.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * OAuth2 令牌响应（tokenType 恒为 Bearer）
 */
@Data
@Builder
@Schema(description = "MCP OAuth2 访问令牌")
public class McpTokenVO {

    private String accessToken;

    private String tokenType;

    @Schema(description = "有效期（秒）")
    private Long expiresIn;

    @Schema(description = "已授予作用域，逗号分隔")
    private String scope;
}

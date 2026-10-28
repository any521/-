package com.dianfei.ai.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 工具调用请求（POST /mcp/tools/call）
 */
@Data
@Schema(description = "MCP 工具调用")
public class McpToolCallDTO {

    @NotBlank(message = "tool 不能为空")
    @Schema(description = "工具名（见 /mcp/tools/list）")
    private String tool;

    @Schema(description = "工具入参")
    private Map<String, Object> params = new HashMap<>();
}

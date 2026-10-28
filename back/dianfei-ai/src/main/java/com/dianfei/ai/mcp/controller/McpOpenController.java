package com.dianfei.ai.mcp.controller;

import com.dianfei.ai.mcp.McpTokenStore;
import com.dianfei.ai.mcp.McpToolRegistry;
import com.dianfei.ai.mcp.dto.McpTokenDTO;
import com.dianfei.ai.mcp.dto.McpTokenVO;
import com.dianfei.ai.mcp.dto.McpToolCallDTO;
import com.dianfei.ai.mcp.service.McpGatewayService;
import com.dianfei.ai.mcp.service.McpOAuthService;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * F10 MCP 第三方开放入口（12 章 /mcp，D17 身份三分之 MCP）：
 * JWT 过滤器对 /mcp/** 匿名放行，本层以 MCP 不透明 Bearer 令牌自行鉴权
 * （{@link McpGatewayService#authenticate}），principal_type=MCP 由审计链标记。
 * 限流（60 req/min）/作用域校验/二次确认挂起均在 McpGatewayService 内完成。
 */
@Tag(name = "MCP-开放网关（F10）")
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpOpenController {

    private final McpOAuthService oauthService;
    private final McpGatewayService gatewayService;

    @Operation(summary = "OAuth2 授权码换访问令牌（grant_type=authorization_code）")
    @PostMapping("/oauth/token")
    public Result<McpTokenVO> token(@Valid @RequestBody McpTokenDTO dto) {
        return Result.success(oauthService.token(dto));
    }

    @Operation(summary = "撤销访问令牌（用户撤销授权 / 客户端登出）")
    @PostMapping("/oauth/revoke")
    public Result<Void> revoke(@RequestParam("token") String token) {
        oauthService.revoke(token);
        return Result.success();
    }

    @Operation(summary = "工具清单 tools/list（08 章 8.2 唯一事实源）")
    @GetMapping("/tools/list")
    public Result<List<McpToolRegistry.McpTool>> toolsList(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        gatewayService.authenticate(authorization);
        return Result.success(McpToolRegistry.TOOLS);
    }

    @Operation(summary = "工具调用 tools/call：读类直接返回；下单类返回 PENDING_CONFIRMATION 待用户确认")
    @PostMapping("/tools/call")
    public Result<Map<String, Object>> toolsCall(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody McpToolCallDTO dto) {
        McpTokenStore.McpGrant grant = gatewayService.authenticate(authorization);
        return Result.success(gatewayService.call(grant, dto.getTool(), dto.getParams()));
    }
}

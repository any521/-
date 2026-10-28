package com.dianfei.ai.mcp.controller;

import com.dianfei.ai.mcp.dto.McpAuthCodeVO;
import com.dianfei.ai.mcp.dto.McpAuthorizeDTO;
import com.dianfei.ai.mcp.entity.McpAudit;
import com.dianfei.ai.mcp.service.McpGatewayService;
import com.dianfei.ai.mcp.service.McpOAuthService;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * F10 MCP 用户侧入口（08 章 8.1）：登录用户在此完成 OAuth2 授权同意，
 * 以及下单类工具（book_recycling / redeem_points）的二次确认（权限最小化）。
 * 与 /mcp/** 第三方入口分离：本层走 JWT 登录态（principal_type=HUMAN）。
 */
@Tag(name = "MCP-用户授权与确认（F10）")
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpUserController {

    private final McpOAuthService oauthService;
    private final McpGatewayService gatewayService;

    @Operation(summary = "用户同意授权：签发 5 分钟单次授权码（客户端回调换令牌）")
    @PostMapping("/authorize")
    public Result<McpAuthCodeVO> authorize(@Valid @RequestBody McpAuthorizeDTO dto) {
        return Result.success(oauthService.authorize(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的待确认列表：第三方发起的下单类调用（10 分钟超时）")
    @GetMapping("/confirmations")
    public Result<List<McpAudit>> confirmations() {
        return Result.success(gatewayService.pendingConfirmations(UserContext.getUserId()));
    }

    @Operation(summary = "二次确认/拒绝：approve=true 按调用时参数快照执行工具")
    @PostMapping("/confirmations/{id}")
    public Result<Map<String, Object>> confirm(@PathVariable("id") Long id,
                                               @RequestParam("approve") boolean approve) {
        return Result.success(gatewayService.confirm(UserContext.getUserId(), id, approve));
    }
}

package com.dianfei.ai.mcp.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.mcp.McpTokenStore;
import com.dianfei.ai.mcp.dto.McpAuthCodeVO;
import com.dianfei.ai.mcp.dto.McpAuthorizeDTO;
import com.dianfei.ai.mcp.dto.McpTokenDTO;
import com.dianfei.ai.mcp.dto.McpTokenVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.entity.SysOauthClient;
import com.dianfei.system.mapper.SysOauthClientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * F10 OAuth2 授权码流（08 章 8.1 / D17 身份三分之 MCP 第三方）：
 * 用户登录态下授权 → 签发 5 分钟单次授权码 → 客户端凭 client_secret 换 2h 访问令牌；
 * 作用域最小化：请求 scope 必须 ⊆ sys_oauth_client.scopes，令牌只携带已授予集合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpOAuthService {

    private final SysOauthClientMapper oauthClientMapper;
    private final McpTokenStore tokenStore;

    /** 用户同意授权：校验客户端/回调地址/作用域后签发授权码 */
    public McpAuthCodeVO authorize(Long userId, McpAuthorizeDTO dto) {
        SysOauthClient client = requireClient(dto.getClientId());
        if (!registeredUris(client).contains(dto.getRedirectUri())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "redirect_uri 未在客户端注册");
        }
        Set<String> granted = requestedScopes(dto.getScope());
        Set<String> allowed = splitScopes(client.getScopes());
        if (!allowed.containsAll(granted)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "请求作用域超出客户端注册范围：" + client.getScopes());
        }
        McpTokenStore.McpGrant grant = new McpTokenStore.McpGrant();
        grant.setClientId(client.getClientId());
        grant.setUserId(userId);
        grant.setScopes(String.join(",", granted));
        grant.setRedirectUri(dto.getRedirectUri());
        String code = tokenStore.issueCode(grant);
        log.info("MCP 授权码签发 client={} user={} scope={}", client.getClientId(), userId, grant.getScopes());
        return McpAuthCodeVO.builder()
                .code(code).state(dto.getState()).redirectUri(dto.getRedirectUri())
                .build();
    }

    /** 授权码换令牌：客户端密钥 BCrypt 校验 + code 单次消费 + redirect_uri 一致性 */
    public McpTokenVO token(McpTokenDTO dto) {
        if (!"authorization_code".equals(dto.getGrantType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 grant_type=authorization_code");
        }
        SysOauthClient client = requireClient(dto.getClientId());
        if (!BCrypt.checkpw(dto.getClientSecret(), client.getClientSecret())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "client_secret 校验失败");
        }
        McpTokenStore.McpGrant grant = tokenStore.consumeCode(dto.getCode());
        if (grant == null || !grant.getClientId().equals(client.getClientId())
                || !grant.getRedirectUri().equals(dto.getRedirectUri())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "授权码无效或已过期");
        }
        String token = tokenStore.issueToken(grant);
        log.info("MCP 令牌签发 client={} user={} scope={}",
                client.getClientId(), grant.getUserId(), grant.getScopes());
        return McpTokenVO.builder()
                .accessToken(token).tokenType("Bearer")
                .expiresIn(McpTokenStore.TOKEN_TTL.toSeconds())
                .scope(grant.getScopes())
                .build();
    }

    /** 撤销访问令牌（用户或客户端均可发起） */
    public void revoke(String token) {
        tokenStore.revokeToken(token);
    }

    public SysOauthClient requireClient(String clientId) {
        SysOauthClient client = oauthClientMapper.selectOne(
                new LambdaQueryWrapper<SysOauthClient>().eq(SysOauthClient::getClientId, clientId));
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "客户端不存在或已禁用");
        }
        return client;
    }

    private Set<String> registeredUris(SysOauthClient client) {
        return splitScopes(client.getRedirectUri());
    }

    private Set<String> requestedScopes(String scope) {
        Set<String> scopes = splitScopes(scope);
        if (scopes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "scope 不能为空");
        }
        return scopes;
    }

    private Set<String> splitScopes(String joined) {
        if (joined == null || joined.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(joined.split("[,\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}

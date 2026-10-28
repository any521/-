package com.dianfei.ai.mcp;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.dianfei.common.utils.RedisUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OAuth2 授权码 / 访问令牌存储（Redis，08 章 8.1）：
 * 授权码 5 分钟单次有效；访问令牌 2h（不透明 Token，撤销即失效——用户可随时撤销授权）
 */
@Component
@RequiredArgsConstructor
public class McpTokenStore {

    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final Duration TOKEN_TTL = Duration.ofHours(2);

    private static final String CODE_PREFIX = "mcp:code:";
    private static final String TOKEN_PREFIX = "mcp:token:";

    private final RedisUtils redisUtils;

    /** 授权快照：授权码与访问令牌共用（clientId + 被代理用户 + 授予作用域） */
    @Data
    public static class McpGrant {
        private String clientId;
        private Long userId;
        /** 已授予作用域，逗号分隔（read,write） */
        private String scopes;
        /** 授权码绑定的回调地址（换取令牌时必须一致） */
        private String redirectUri;
    }

    public String issueCode(McpGrant grant) {
        String code = IdUtil.fastSimpleUUID();
        redisUtils.set(CODE_PREFIX + code, JSONUtil.toJsonStr(grant), CODE_TTL);
        return code;
    }

    /** 消费授权码（单次有效：读取即删除） */
    public McpGrant consumeCode(String code) {
        String json = redisUtils.getAndDelete(CODE_PREFIX + code);
        if (json == null) {
            return null;
        }
        return JSONUtil.toBean(json, McpGrant.class);
    }

    public String issueToken(McpGrant grant) {
        String token = IdUtil.fastSimpleUUID();
        redisUtils.set(TOKEN_PREFIX + token, JSONUtil.toJsonStr(grant), TOKEN_TTL);
        return token;
    }

    public McpGrant getToken(String token) {
        String json = redisUtils.get(TOKEN_PREFIX + token);
        return json == null ? null : JSONUtil.toBean(json, McpGrant.class);
    }

    /** 撤销令牌（用户撤销授权 / 客户端登出） */
    public boolean revokeToken(String token) {
        return redisUtils.delete(TOKEN_PREFIX + token);
    }
}

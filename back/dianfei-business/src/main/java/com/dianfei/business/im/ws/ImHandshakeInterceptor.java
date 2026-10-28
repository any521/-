package com.dianfei.business.im.ws;

import com.dianfei.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * IM 握手拦截器（onsite03）：WS 握手 GET 无法携带 Authorization header，
 * token 走 query 参数（C-O3-1：ws://host/api/ws/im?token=xxx），
 * 在 Security 放行 /api/ws/** 后由本拦截器自校验（JwtAuthFilter 只拦 HTTP）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "imUserId";

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;
        // query 形如 token=xxx（手工解析避免依赖 UriComponents 解析 query 空串）
        String rawQuery = request.getURI().getRawQuery();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                if (pair.startsWith("token=")) {
                    token = pair.substring("token=".length());
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            log.warn("IM 握手拒绝：缺少 token 参数");
            return false;
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            if (jwtUtils.isRefreshToken(claims)) {
                log.warn("IM 握手拒绝：Refresh Token 不允许建立连接");
                return false;
            }
            Number userId = claims.get(JwtUtils.CLAIM_USER_ID, Number.class);
            if (userId == null) {
                log.warn("IM 握手拒绝：token 无 userId");
                return false;
            }
            attributes.put(ATTR_USER_ID, userId.longValue());
            return true;
        } catch (Exception e) {
            log.warn("IM 握手拒绝：token 非法或过期：{}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}

package com.dianfei.business.im.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * IM WebSocket 配置（onsite03）：注册 /api/ws/im（C-O3-1 通道契约）。
 * H5 同域部署（Nginx /api/ws/ 反代）无跨域；安全由握手拦截器 token 校验兜底。
 * <p>
 * S-02 修复：Origin 白名单不再放开 *，改为读取配置项 {@code dianfei.cors.allowed-origins}
 * 或环境变量 {@code DIANFEI_CORS_ALLOWED_ORIGINS}（按逗号分割）。为空时仅允许 localhost（dev 用）。
 * <p>
 * 生产环境必须配置 DIANFEI_CORS_ALLOWED_ORIGINS，显式列出前端域名。
 */
@Slf4j
@Configuration
@EnableWebSocket
public class ImWebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;
    private final ImHandshakeInterceptor imHandshakeInterceptor;

    /**
     * 允许的 Origin 白名单。
     * 优先读取配置项 dianfei.cors.allowed-origins（绑定环境变量 DIANFEI_CORS_ALLOWED_ORIGINS），
     * 按逗号分割；为空时默认仅允许 localhost（本地开发用）。
     */
    @Value("${dianfei.cors.allowed-origins:}")
    private String allowedOriginsConfig;

    public ImWebSocketConfig(ImWebSocketHandler imWebSocketHandler,
                             ImHandshakeInterceptor imHandshakeInterceptor) {
        this.imWebSocketHandler = imWebSocketHandler;
        this.imHandshakeInterceptor = imHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imWebSocketHandler, "/api/ws/im")
                .addInterceptors(imHandshakeInterceptor)
                .setAllowedOrigins(resolveAllowedOrigins().toArray(new String[0]));
    }

    /**
     * 解析允许的 Origin 列表：
     * 1. 读取 dianfei.cors.allowed-origins（经 DIANFEI_CORS_ALLOWED_ORIGINS 环境变量注入），逗号分割；
     * 2. 为空时回退默认白名单（仅 localhost，dev 用），生产环境必须配置环境变量。
     */
    private List<String> resolveAllowedOrigins() {
        List<String> origins;
        if (allowedOriginsConfig != null && !allowedOriginsConfig.isBlank()) {
            origins = Arrays.stream(allowedOriginsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } else {
            // 默认仅允许 localhost + 本地三端 dev 端口（与 CorsConfig 默认白名单对齐）；
            // 生产环境必须配置 DIANFEI_CORS_ALLOWED_ORIGINS
            origins = List.of(
                    "http://localhost:8080",
                    "http://127.0.0.1:8080",
                    "http://localhost",
                    "http://127.0.0.1",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:5174",
                    "http://127.0.0.1:5174",
                    "http://localhost:5175",
                    "http://127.0.0.1:5175"
            );
            log.warn("[WebSocket] dianfei.cors.allowed-origins 未配置，回退默认 localhost 白名单。生产环境必须配置 DIANFEI_CORS_ALLOWED_ORIGINS 环境变量。");
        }
        return origins;
    }
}

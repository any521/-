package com.dianfei.system.security;

import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 6.2 无状态配置（D3 / 04.8）：
 * JWT 过滤器认证，未认证 401 / 无权限 403 返回真实 HTTP 状态码 + Result body（C20 修订 D4）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 匿名放行路径：认证入口 / 注册 / 验证码 / 健康检查 / 接口文档 / 静态与监控 / MCP 第三方入口（自行以不透明令牌鉴权，D17） / AI 配置内部拉取（X-Internal-Token 鉴权） */
    public static final String[] PERMIT_ALL = {
            "/api/health", // S1：健康检查探活（无敏感信息，仅 UP/DOWN 状态）
            "/api/auth/login",
            "/api/auth/login/code",
            "/api/auth/refresh",
            "/api/user/register",
            "/api/user/verify-code",
            "/api/staff/areas", // D0: 片区列表公开可读（地址选择器动态加载）
            "/api/staff/areas/auto-create", // D0: 片区自动创建
            // C11：仅公开业务目录（头像/商品/社区 DIY/闲置/common 素材）匿名可读；
            // 私密目录（im/repair/recycle/kitchen）已从匿名放行移除，必须登录（JwtAuthFilter 支持 query token 供 IM 媒体 <img>/<video>/<audio> 直链）
            "/api/file/preview/avatar/**",
            "/api/file/preview/product/**",
            "/api/file/preview/diy/**",
            "/api/file/preview/flea/**",
            "/api/file/preview/common/**",
            "/api/ws/**",
            "/mcp/**",
            "/api/ai/config/runtime",
            // 地图配置下发：web_key/security_code 本就在浏览器端公开（script URL 携带），
            // 积分商城/回收站点等公开页需要匿名拉取，故放行（server_key 严禁出网，见 MapConfigVO）
            "/api/map/config",
            // W-01/W-02: Actuator 公开监控端点（仅 health/info/prometheus；/actuator/env 等敏感端点不在白名单，需鉴权）
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/favicon.ico",
            "/error"
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL).permitAll()
                        // C12：Druid 监控台不再匿名放行，仅 ADMIN 可访问（口令另经环境变量注入，见 application-dev.yml）
                        .requestMatchers("/druid/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, e) ->
                                writeResult(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Result.failed(ResultCode.UNAUTHORIZED)))
                        .accessDeniedHandler((request, response, e) ->
                                writeResult(response, HttpServletResponse.SC_FORBIDDEN,
                                        Result.failed(ResultCode.FORBIDDEN))))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeResult(HttpServletResponse response, int status, Result<?> result) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (Exception ignored) {
            // 响应已提交等场景直接忽略
        }
    }
}

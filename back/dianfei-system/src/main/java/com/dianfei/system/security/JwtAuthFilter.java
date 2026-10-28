package com.dianfei.system.security;

import com.dianfei.common.enums.ClientTypeEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import com.dianfei.common.utils.JwtUtils;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import com.dianfei.system.mapper.StaffInfoMapper;
import com.dianfei.system.vo.StaffInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证过滤器：
 * 1. 解析 Authorization: Bearer <accessToken>，合法则填充 UserContext + SecurityContext
 * 2. X-Client-Type 多端识别写入 LoginUser
 * 3. Agent 系统账号（principal_type=AGENT）仅允许访问接口白名单（对齐 04.8）
 * 请求结束统一清理 ThreadLocal
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    /** C20c：禁用/删除账号 token 即时失效（每个请求校验一次状态） */
    private final SysUserMapper sysUserMapper;
    /** STAFF 端在岗员工角色映射（与签发 token 时一致，避免 sys_user.role 与员工类型不一致时角色错乱） */
    private final StaffInfoMapper staffInfoMapper;

    /** Agent 系统账号接口白名单（Ant 风格），yml 配置 dianfei.auth.agent-whitelist */
    @Value("${dianfei.auth.agent-whitelist:}")
    private List<String> agentWhitelist;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null) {
                Claims claims;
                try {
                    claims = jwtUtils.parseToken(token);
                } catch (JwtException e) {
                    // Token 非法/过期：交给后续 permitAll 判定；受保护接口由 EntryPoint 返回 401
                    chain.doFilter(request, response);
                    return;
                }
                // 仅 Access Token 可访问业务接口，Refresh Token 只能用于 /api/auth/refresh
                if (jwtUtils.isRefreshToken(claims)) {
                    chain.doFilter(request, response);
                    return;
                }
                LoginUser user = jwtUtils.toLoginUser(claims);
                ClientTypeEnum clientType = ClientTypeEnum.of(request.getHeader(ClientTypeEnum.HEADER));
                user.setClientType(clientType == null ? null : clientType.name());

                // C20c：禁用/删除账号失效校验——查询库实时状态，禁用后已签发 token 即刻不可用
                SysUser dbUser = sysUserMapper.selectById(user.getUserId());
                if (dbUser == null || dbUser.getStatus() == null || dbUser.getStatus() != 1) {
                    writeUnauthorized(response, "账号已禁用或不存在");
                    return;
                }
                // 绑定最新角色/状态到本次请求上下文，避免持有旧角色访问新建授权
                String role = dbUser.getRole();
                // STAFF 端请求：在岗员工按 biz_staff.staff_type 映射员工角色（与登录签发 token 时 resolveStaffRole 一致）。
                // 否则 sys_user.role 为 USER 的在岗技术员（如 App 申请入职审核通过）会被覆盖成 USER，
                // 前端 ROLE_MAP 无匹配回退 RECYCLER，导致维修员看到回收订单。
                if (ClientTypeEnum.STAFF.name().equals(clientType == null ? null : clientType.name())) {
                    StaffInfoVO info = staffInfoMapper.selectLatestByUserId(user.getUserId());
                    if (info != null && info.getAuditStatus() != null && info.getAuditStatus() == 1
                            && info.getStatus() != null && info.getStatus() == 1) {
                        StaffTypeEnum type = StaffTypeEnum.of(info.getStaffType());
                        if (type != null) {
                            role = type.name();
                        }
                    }
                }
                user.setRole(role);

                // Agent 白名单校验
                if (LoginUser.PRINCIPAL_AGENT.equals(user.getPrincipalType())
                        && !inAgentWhitelist(request.getRequestURI())) {
                    writeForbidden(response, "Agent 账号不在接口白名单内");
                    return;
                }

                UserContext.set(user);
                var authToken = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        // C11：IM 媒体（<img>/<video>/<audio>）无法携带 Header，仅允许私密 preview 路径
        // 使用 query token（access_token）直链；其它接口不支持 query token，防 URL 泄露面扩大
        if (request.getRequestURI().startsWith("/api/file/preview/")) {
            String queryToken = request.getParameter("access_token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
    }

    private boolean inAgentWhitelist(String uri) {
        return agentWhitelist != null && agentWhitelist.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern.trim(), uri));
    }

    private void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.failed(ResultCode.FORBIDDEN, msg)));
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.failed(ResultCode.UNAUTHORIZED, msg)));
    }
}

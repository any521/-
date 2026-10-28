package com.dianfei.system.controller;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.core.util.StrUtil;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.enums.ClientTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import com.dianfei.system.dto.LoginByCodeDTO;
import com.dianfei.system.dto.LoginDTO;
import com.dianfei.system.dto.RefreshDTO;
import com.dianfei.system.service.AuthService;
import com.dianfei.system.vo.TokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.util.Arrays;
import java.util.Locale;

/**
 * 认证接口（12 章契约：/api/auth/*）
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Operation(summary = "登录（BCrypt 校验 + 签发双 Token）")
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO dto,
                                 @RequestHeader(value = ClientTypeEnum.HEADER, required = false) String clientType,
                                 @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                 HttpServletResponse response) {
        TokenVO token = authService.login(dto, clientType, fingerprint(userAgent));
        writeRefreshCookie(response, clientType, token.getRefreshToken(), false);
        token.setRefreshToken(null);
        return Result.success(token);
    }

    @Operation(summary = "邮箱验证码登录（验证码即身份证明，未注册自动建号，不累计失败锁定）")
    @PostMapping("/login/code")
    public Result<TokenVO> loginByCode(@Valid @RequestBody LoginByCodeDTO dto,
                                       @RequestHeader(value = ClientTypeEnum.HEADER, required = false) String clientType,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       HttpServletResponse response) {
        TokenVO token = authService.loginByCode(dto, clientType, fingerprint(userAgent));
        writeRefreshCookie(response, clientType, token.getRefreshToken(), false);
        token.setRefreshToken(null);
        return Result.success(token);
    }

    @Operation(summary = "刷新双令牌（Refresh Token 轮换）")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@RequestBody(required = false) RefreshDTO dto,
                                   @RequestHeader(value = ClientTypeEnum.HEADER, required = false) String clientType,
                                   @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        if (!StrUtil.isNotBlank(clientType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请指定客户端类型（ADMIN / USER）");
        }
        // 同主机多端共用 Cookie：按请求方客户端取专属 Cookie（df_refresh_{ADMIN/USER/STAFF}），防跨端串号
        String refreshToken = dto != null && dto.getRefreshToken() != null && !dto.getRefreshToken().isBlank()
                ? dto.getRefreshToken() : resolveRefreshCookie(request, clientType);
        TokenVO token = authService.refresh(refreshToken, fingerprint(userAgent), clientType);
        writeRefreshCookie(response, clientType, token.getRefreshToken(), false);
        token.setRefreshToken(null);
        return Result.success(token);
    }

    @Operation(summary = "切换工作人员模式（双身份互通：当前 USER 会话换取 STAFF 会话并种 STAFF Refresh Cookie）")
    @PostMapping("/switch-staff")
    public Result<TokenVO> switchStaff(@RequestHeader(value = ClientTypeEnum.HEADER, required = false) String clientType,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       HttpServletResponse response) {
        TokenVO token = authService.switchToStaff(fingerprint(userAgent));
        writeRefreshCookie(response, ClientTypeEnum.STAFF.name(), token.getRefreshToken(), false);
        token.setRefreshToken(null);
        return Result.success(token);
    }

    @Operation(summary = "切回用户模式（双身份互通：当前 STAFF 会话换回 USER 会话并种 USER Refresh Cookie）")
    @PostMapping("/switch-user")
    public Result<TokenVO> switchUser(@RequestHeader(value = ClientTypeEnum.HEADER, required = false) String clientType,
                                      @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                      HttpServletResponse response) {
        TokenVO token = authService.switchToUser(fingerprint(userAgent));
        writeRefreshCookie(response, ClientTypeEnum.USER.name(), token.getRefreshToken(), false);
        token.setRefreshToken(null);
        return Result.success(token);
    }

    @Operation(summary = "登出（删除 Refresh Token）")
    @OperLog(value = "登出", module = "认证")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout();
        // 只清当前端专属 Cookie：同主机多端（/admin /user /staff）各自登出互不牵连，
        // 避免员工端登出把用户端的 df_refresh_USER 一并清掉导致用户端"突然失效"
        LoginUser user = UserContext.get();
        String clientType = user == null ? null : user.getClientType();
        if (StrUtil.isBlank(clientType)) {
            clearRefreshCookies(request, response); // 未登录态登出兜底：全清
        } else {
            Arrays.stream(request.getCookies() == null ? new Cookie[0] : request.getCookies())
                    .filter(c -> cookieName(clientType).equals(c.getName()))
                    .forEach(c -> response.addHeader("Set-Cookie", refreshCookie(c.getName(), "", true).build().toString()));
        }
        return Result.success();
    }

    /** Refresh Cookie 名：df_refresh_{客户端}，同主机多端（/admin /user /staff + 各 dev 端口）互不干扰 */
    private String cookieName(String clientType) {
        ClientTypeEnum ct = ClientTypeEnum.of(clientType);
        return "df_refresh_" + (ct != null ? ct.name() : StrUtil.blankToDefault(clientType, "").toUpperCase(Locale.ROOT));
    }

    private String resolveRefreshCookie(HttpServletRequest request, String clientType) {
        if (request.getCookies() == null) {
            return null;
        }
        String target = cookieName(clientType);
        return Arrays.stream(request.getCookies())
                .filter(c -> target.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }

    private void clearRefreshCookies(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() == null) {
            return;
        }
        Arrays.stream(request.getCookies())
                .filter(c -> c.getName() != null && c.getName().startsWith("df_refresh_"))
                .map(Cookie::getName)
                .distinct()
                .forEach(name -> response.addHeader("Set-Cookie", refreshCookie(name, "", true).build().toString()));
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookie(String name, String token, boolean clear) {
        // Path=/ 而非 /api/auth：子路径部署（/点废成金/api/auth）下请求路径不以 /api/auth 开头，
        // 浏览器不会携带该 Cookie → 刷新页面后内存令牌丢失且续期拿不到 Refresh Token → 刷新即登出（2026-08 线上缺陷）。
        // Cookie 名已按客户端区分（df_refresh_{ADMIN/USER/STAFF}），Path=/ 同主机多端共享无串号风险。
        return ResponseCookie.from(name, token == null ? "" : token)
                .httpOnly(true).secure(activeProfiles != null && activeProfiles.contains("prod"))
                .sameSite("Lax").path("/").maxAge(clear ? 0 : 7 * 24 * 60 * 60L);
    }

    private void writeRefreshCookie(HttpServletResponse response, String clientType, String token, boolean clear) {
        response.addHeader("Set-Cookie", refreshCookie(cookieName(clientType), token, clear).build().toString());
    }

    /** 客户端指纹：User-Agent 摘要（D3：Refresh Token 绑定指纹） */
    private String fingerprint(String userAgent) {
        return userAgent == null ? "" : SecureUtil.md5(userAgent);
    }
}

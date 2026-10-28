package com.dianfei.common.utils;

import com.dianfei.common.security.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 双令牌工具（JJWT 0.12）
 * Access Token 2h 存内存；Refresh Token 7d 存 Redis 并绑定客户端指纹（D3）
 */
@Component
public class JwtUtils {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_AREA_ID = "areaId";
    public static final String CLAIM_PRINCIPAL_TYPE = "principalType";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessExpire;
    private final Duration refreshExpire;

    public JwtUtils(@Value("${dianfei.jwt.secret}") String secret,
                    @Value("${dianfei.jwt.access-expire-minutes:120}") long accessExpireMinutes,
                    @Value("${dianfei.jwt.refresh-expire-days:7}") long refreshExpireDays) {
        // C6 安全加固：JWT secret 必须显式注入强随机值（≥32 字节），缺失/过短直接拒绝启动
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "dianfei.jwt.secret 未配置或长度不足 32 字节（HS256 最低要求）。"
                            + "请通过环境变量 JWT_SECRET 注入强随机密钥，禁止使用默认值/硬编码密钥。");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpire = Duration.ofMinutes(accessExpireMinutes);
        this.refreshExpire = Duration.ofDays(refreshExpireDays);
    }

    /** 签发 Access Token */
    public String createAccessToken(LoginUser user) {
        return createToken(user, TOKEN_TYPE_ACCESS, accessExpire);
    }

    /** 签发 Refresh Token（jti 唯一，调用方负责存 Redis 绑定指纹） */
    public String createRefreshToken(LoginUser user) {
        return createToken(user, TOKEN_TYPE_REFRESH, refreshExpire);
    }

    private String createToken(LoginUser user, String tokenType, Duration expire) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getUserId());
        claims.put(CLAIM_USERNAME, user.getUsername());
        claims.put(CLAIM_ROLE, user.getRole());
        claims.put(CLAIM_AREA_ID, user.getAreaId());
        claims.put(CLAIM_PRINCIPAL_TYPE, user.getPrincipalType());
        claims.put(CLAIM_TOKEN_TYPE, tokenType);
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(claims)
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire.toMillis()))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 Token，非法/过期抛 JwtException */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    /** 从 Claims 还原登录主体 */
    public LoginUser toLoginUser(Claims claims) {
        Number userId = claims.get(CLAIM_USER_ID, Number.class);
        Number areaId = claims.get(CLAIM_AREA_ID, Number.class);
        return LoginUser.builder()
                .userId(userId == null ? null : userId.longValue())
                .username(claims.get(CLAIM_USERNAME, String.class))
                .role(claims.get(CLAIM_ROLE, String.class))
                .areaId(areaId == null ? null : areaId.longValue())
                .principalType(claims.get(CLAIM_PRINCIPAL_TYPE, String.class))
                .build();
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public Duration getRefreshExpire() {
        return refreshExpire;
    }

    public Duration getAccessExpire() {
        return accessExpire;
    }
}

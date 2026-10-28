package com.dianfei.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.common.enums.ClientTypeEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import com.dianfei.common.utils.JwtUtils;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.system.dto.LoginByCodeDTO;
import com.dianfei.system.dto.LoginDTO;
import com.dianfei.system.dto.RegisterDTO;
import com.dianfei.system.dto.SetPasswordDTO;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import com.dianfei.system.mapper.StaffInfoMapper;
import com.dianfei.system.service.AuthService;
import com.dianfei.system.service.LogService;
import com.dianfei.system.vo.StaffInfoVO;
import com.dianfei.system.vo.TokenVO;
import cn.hutool.extra.servlet.JakartaServletUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务实现（D3 双令牌 / 04.8 身份三分之 HUMAN）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Refresh Token 存储键：dianfei:auth:refresh:{userId}:{clientType} → jti|fingerprint */
    private static final String REFRESH_KEY = "dianfei:auth:refresh:%s:%s";
    private static final String CODE_KEY = "dianfei:auth:code:%s";
    private static final String CODE_LIMIT_KEY = "dianfei:auth:code:limit:%s";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration CODE_LIMIT_TTL = Duration.ofSeconds(60);

    // C20b 登录失败锁定：窗口内连续失败 MAX_FAILS 次 → 锁定 LOCK_TTL（按用户名维度，防密码爆破）
    private static final String LOGIN_FAIL_KEY = "dianfei:auth:fail:%s";
    private static final String LOGIN_LOCK_KEY = "dianfei:auth:lock:%s";
    private static final int LOGIN_MAX_FAILS = 5;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(10);

    /** 端 → 允许登录的角色（X-Client-Type 多端角色校验） */
    private static final Set<String> STAFF_ROLES = Set.of("RECYCLER", "SANITATION", "TECHNICIAN");

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final LogService logService;
    private final JavaMailSender mailSender;
    private final StaffInfoMapper staffInfoMapper;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Override
    public TokenVO login(LoginDTO dto, String clientType, String fingerprint) {
        try {
            TokenVO token = doLogin(dto, clientType, fingerprint);
            recordLoginLog(dto.getUsername(), clientType, 0, null);
            return token;
        } catch (BusinessException e) {
            recordLoginLog(dto.getUsername(), clientType, 1, e.getMessage());
            throw e;
        }
    }

    private TokenVO doLogin(LoginDTO dto, String clientType, String fingerprint) {
        // C20b：锁定期内直接拒绝，不再比对密码（防爆破）
        String lockKey = LOGIN_LOCK_KEY.formatted(dto.getUsername());
        if (redisUtils.hasKey(lockKey)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "失败次数过多，账号已锁定，请 15 分钟后再试");
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .or().eq(SysUser::getEmail, dto.getUsername())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            recordLoginFail(dto.getUsername());
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        // 登录成功：清失败计数与锁定
        redisUtils.delete(LOGIN_FAIL_KEY.formatted(dto.getUsername()));
        redisUtils.delete(lockKey);
        checkClientRole(clientType, user.getRole(), user.getId());
        return issueTokens(user, clientType, fingerprint);
    }

    /** C20b：失败计数，窗口内达上限则置锁定键 */
    private void recordLoginFail(String username) {
        String failKey = LOGIN_FAIL_KEY.formatted(username);
        long count = redisUtils.increment(failKey, LOGIN_FAIL_WINDOW);
        if (count >= LOGIN_MAX_FAILS) {
            redisUtils.set(LOGIN_LOCK_KEY.formatted(username), "1", LOGIN_LOCK_TTL);
            redisUtils.delete(failKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenVO loginByCode(LoginByCodeDTO dto, String clientType, String fingerprint) {
        // 验证码即身份证明：不比对密码、不累计失败锁定，彻底规避派生密码不一致导致的"一直报错→锁定"问题
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        consumeVerifyCode(email, dto.getVerifyCode());
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, email)
                .or().eq(SysUser::getEmail, email)
                .last("LIMIT 1"));
        if (user == null) {
            // 未注册自动建号（密码用与前端登录桥同款的派生公式，兼容后续密码登录链路）
            user = new SysUser();
            user.setUsername(email);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(derivedPassword(email)));
            user.setNickname(StrUtil.subBefore(email, '@', false));
            user.setRole("USER");
            user.setPrincipalType(LoginUser.PRINCIPAL_HUMAN);
            user.setStatus(1);
            sysUserMapper.insert(user);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        // 验证码登录证明持有邮箱：清除历史失败计数与锁定
        redisUtils.delete(LOGIN_FAIL_KEY.formatted(email));
        redisUtils.delete(LOGIN_LOCK_KEY.formatted(email));
        checkClientRole(clientType, user.getRole(), user.getId());
        TokenVO token = issueTokens(user, clientType, fingerprint);
        recordLoginLog(email, clientType, 0, null);
        return token;
    }

    /** 派生密码：与前端登录桥同款公式（Df@ + 邮箱前缀后 6 位 + 1），仅用于验证码建号时占位 */
    private String derivedPassword(String email) {
        String prefix = StrUtil.subBefore(email, '@', false);
        String tail = prefix.length() > 6 ? prefix.substring(prefix.length() - 6) : prefix;
        return "Df@" + tail + "1";
    }

    @Override
    public TokenVO refresh(String refreshToken, String fingerprint, String expectedClientType) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 缺失");
        }
        Claims claims;
        try {
            claims = jwtUtils.parseToken(refreshToken);
        } catch (JwtException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 非法或已过期");
        }
        if (!jwtUtils.isRefreshToken(claims)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "非 refreshToken");
        }
        LoginUser loginUser = jwtUtils.toLoginUser(claims);
        String clientType = StrUtil.blankToDefault(loginUser.getClientType(), guessClient(loginUser.getRole()));
        // 同主机多端共用 Cookie：令牌归属客户端必须与请求方一致，防止用户端拿到管理端会话后 403
        String tokenClient = normalizeClientType(clientType);
        String headerClient = normalizeClientType(expectedClientType);
        if (StrUtil.isNotBlank(tokenClient) && StrUtil.isNotBlank(headerClient) && !tokenClient.equals(headerClient)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌与客户端不匹配，请重新登录");
        }
        String lockKey = "dianfei:auth:refresh:lock:" + loginUser.getUserId() + ":" + clientType;
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.setIfAbsent(lockKey, lockValue, REFRESH_LOCK_TTL)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "刷新请求正在处理中，请稍后重试");
        }
        try {
            String stored = redisUtils.get(REFRESH_KEY.formatted(loginUser.getUserId(), clientType));
            if (stored == null) {
                // 从未签发 / 已登出 / 已过期：真正失效，必须重新登录
                throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已失效，请重新登录");
            }
            String expected = claims.getId() + "|" + StrUtil.nullToEmpty(fingerprint);
            if (!stored.equals(expected)) {
                // 轮换冲突：同设备多 tab / 双端并发刷新时，先到者已轮换令牌，旧 token 重放至此；
                // 返回 429 让前端用最新 Cookie 中的新令牌退避重试，而不是 401 直接踢出登录
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "会话已刷新，请重试");
            }
            // 重新加载用户（角色/状态可能已变更）
            SysUser user = sysUserMapper.selectById(loginUser.getUserId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不可用，请重新登录");
            }
            // 锁内轮换，旧 refresh 只能成功一次
            return issueTokens(user, clientType, fingerprint);
        } finally {
            redisUtils.deleteIfValue(lockKey, lockValue);
        }
    }

    @Override
    public void logout() {
        LoginUser user = UserContext.get();
        if (user != null) {
            String clientType = StrUtil.blankToDefault(user.getClientType(), guessClient(user.getRole()));
            redisUtils.delete(REFRESH_KEY.formatted(user.getUserId(), clientType));
        }
    }

    @Override
    public void sendVerifyCode(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        // ✅ 修改：不阻塞同窗口内的重发（验证码集合模式下允许多码并存）
        if (!redisUtils.setIfAbsent(CODE_LIMIT_KEY.formatted(normalized), "1", CODE_LIMIT_TTL)) {
            log.info("[验证码] 限流窗口内请求跳过 email={}", normalized);
            return; // ✅ 同一限流窗口内的请求直接返回，不报错
        }
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, "点废成金");
            helper.setTo(normalized);
            helper.setSubject("【点废成金】邮箱验证码");
            helper.setText("您好！\n\n您的验证码是：" + code + "，5 分钟内有效，请勿泄露给他人。\n\n" +
                    "若非本人操作，请忽略本邮件。");
            mailSender.send(message);
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e ) {
            // 发送失败不占用限流窗口，允许立即重试
            redisUtils.delete(CODE_LIMIT_KEY.formatted(normalized));
            log.error("[验证码] 邮件发送失败 email={}", normalized, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "验证码发送失败，请稍后再试");
        }
        // 多码槽：重发不覆盖旧码（CODE_TTL 内已发出的码全部有效），避免用户拿着邮箱里的旧码输入报"错误"；
        // 码量受 1 次/分钟限流约束（5 分钟内至多 6 个），集合随 TTL 自动过期清理
        redisUtils.sAdd(CODE_KEY.formatted(normalized), code);
        redisUtils.expire(CODE_KEY.formatted(normalized), CODE_TTL);
        log.info("[验证码] 邮件已发送 email={}", normalized);
    }

    /** 校验验证码并一次性作废（区分"已过期"与"错误"，提示更明确） */
    private void consumeVerifyCode(String email, String verifyCode) {
        String codeKey = CODE_KEY.formatted(email);
        if (!redisUtils.hasKey(codeKey)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码已过期，请重新获取");
        }
        if (!redisUtils.sIsMember(codeKey, verifyCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误");
        }
        redisUtils.delete(codeKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .or().eq(SysUser::getUsername, email));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该邮箱已注册");
        }
        consumeVerifyCode(email, dto.getVerifyCode());
        SysUser user = new SysUser();
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StrUtil.blankToDefault(dto.getNickname(), StrUtil.subBefore(email, '@', false)));
        user.setRole("USER");
        user.setPrincipalType(LoginUser.PRINCIPAL_HUMAN);
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    @Override
    public void setPassword(SetPasswordDTO dto) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在");
        }
        // 验证码 = 持有邮箱凭证（替代旧密码，兼容验证码注册的无密码可见账号）
        if (StrUtil.isBlank(user.getEmail())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前账号未绑定邮箱，无法设置密码");
        }
        consumeVerifyCode(user.getEmail(), dto.getVerifyCode());
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
    }

    /** 签发双令牌并把 Refresh 的 jti+指纹存 Redis（同端重复登录旧令牌自动挤下线） */
    private TokenVO issueTokens(SysUser user, String clientType, String fingerprint) {
        String role = user.getRole();
        // 双身份互通：STAFF 端会话 role 映射为在岗员工角色（USER 账号经审核上岗后登录/切换员工端）
        if (ClientTypeEnum.STAFF.name().equals(normalizeClientType(clientType))) {
            String staffRole = resolveStaffRole(user.getId());
            if (staffRole != null) {
                role = staffRole;
            }
        }
        // 双身份互通对称：USER 端会话 role 映射为 USER（员工账号登录/切回用户端以市民身份使用）
        if (ClientTypeEnum.USER.name().equals(normalizeClientType(clientType)) && STAFF_ROLES.contains(role)) {
            role = "USER";
        }
        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(role)
                .areaId(user.getAreaId())
                .principalType(StrUtil.blankToDefault(user.getPrincipalType(), LoginUser.PRINCIPAL_HUMAN))
                .clientType(clientType)
                .build();
        String accessToken = jwtUtils.createAccessToken(loginUser);
        String refreshToken = jwtUtils.createRefreshToken(loginUser);
        String jti = jwtUtils.parseToken(refreshToken).getId();
        redisUtils.set(REFRESH_KEY.formatted(user.getId(), clientType),
                jti + "|" + StrUtil.nullToEmpty(fingerprint), jwtUtils.getRefreshExpire());
        return TokenVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getAccessExpire().toSeconds())
                .build();
    }

    /** STAFF 会话角色映射：在岗员工身份的 staffType → 员工角色枚举；非在岗返回 null（保持原角色） */
    private String resolveStaffRole(Long userId) {
        if (userId == null) {
            return null;
        }
        StaffInfoVO info = staffInfoMapper.selectLatestByUserId(userId);
        if (info == null || info.getAuditStatus() == null || info.getAuditStatus() != 1
                || info.getStatus() == null || info.getStatus() != 1) {
            return null;
        }
        StaffTypeEnum type = StaffTypeEnum.of(info.getStaffType());
        return type == null ? null : type.name();
    }

    /** 双身份互通：USER 角色用户在岗（auditStatus=1 且 status=1）时可登录/切换员工端 */
    private boolean isApprovedStaff(Long userId) {
        return resolveStaffRole(userId) != null;
    }

    @Override
    public TokenVO switchToStaff(String fingerprint) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!isApprovedStaff(loginUser.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非在岗员工，无法切换到工作人员模式");
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不可用，请重新登录");
        }
        // STAFF 会话独立刷新链路：issueTokens 内部已做 role 映射并写 STAFF 专属 Refresh 键
        return issueTokens(user, ClientTypeEnum.STAFF.name(), fingerprint);
    }

    @Override
    public TokenVO switchToUser(String fingerprint) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不可用，请重新登录");
        }
        // USER 会话独立刷新链路：同一 userId 的 USER 专属 Refresh 键（df_refresh_USER），
        // 与 STAFF 会话互不覆盖；用户端接口按 USER 角色鉴权，由 issueTokens 重新签发 USER 角色令牌
        return issueTokens(user, ClientTypeEnum.USER.name(), fingerprint);
    }

    /** X-Client-Type 多端角色校验（对齐 A3；双身份互通：STAFF 端放行在岗的 USER 角色用户） */
    private void checkClientRole(String clientType, String role, Long userId) {
        ClientTypeEnum client = ClientTypeEnum.of(clientType);
        if (client == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少或非法的 X-Client-Type 请求头");
        }
        boolean allowed = switch (client) {
            // 双身份互通（C-O1）：员工账号（RECYCLER/SANITATION/TECHNICIAN）登录/切回用户端
            // 以市民身份使用（issueTokens 会将其映射为 USER 角色）；ADMIN 仍不允许登录用户端
            case USER -> "USER".equals(role) || STAFF_ROLES.contains(role);
            case STAFF -> STAFF_ROLES.contains(role) || isApprovedStaff(userId);
            case ADMIN -> "ADMIN".equals(role);
        };
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号不允许在此端登录");
        }
    }

    private String guessClient(String role) {
        if ("ADMIN".equals(role)) {
            return ClientTypeEnum.ADMIN.name();
        }
        return STAFF_ROLES.contains(role) ? ClientTypeEnum.STAFF.name() : ClientTypeEnum.USER.name();
    }

    /** 客户端类型规范化：合法枚举取规范名，否则原样大写（兼容旧小写头） */
    private String normalizeClientType(String raw) {
        ClientTypeEnum ct = ClientTypeEnum.of(raw);
        return ct != null ? ct.name() : StrUtil.blankToDefault(raw, "").toUpperCase(Locale.ROOT);
    }

    /** 同步线程捕获 IP/UA 后交给 LogService 异步落库 */
    private void recordLoginLog(String username, String clientType, Integer status, String msg) {
        String loginIp = null;
        String userAgent = null;
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            loginIp = JakartaServletUtil.getClientIP(request);
            userAgent = StrUtil.subPre(request.getHeader("User-Agent"), 500);
        }
        logService.recordLoginLog(username, clientType, status, msg, loginIp, userAgent);
    }
}

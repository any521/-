package com.dianfei.common.aspect;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.UserContext;
import com.dianfei.common.utils.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 幂等切面：Redis SETNX（键 = 前缀 + 主体标识 + 参数摘要），窗口期内重复调用直接拒绝
 * 主体标识：已登录用 userId；匿名（未登录/游客）退化用客户端 IP，避免所有匿名请求共享同一键（C20d）
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String KEY_PREFIX = "idempotent:";

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String prefix = idempotent.prefix().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : idempotent.prefix();
        String digest = DigestUtil.md5Hex(buildParamString(joinPoint.getArgs()));
        String subject = resolveSubject();
        String key = KEY_PREFIX + prefix + ":" + subject + ":" + digest;

        Duration timeout = Duration.ofMillis(
                idempotent.timeUnit().toMillis(idempotent.timeout()));
        boolean acquired = redisUtils.setIfAbsent(key, "1", timeout);
        if (!acquired) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, idempotent.message());
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 执行失败释放幂等键，允许调用方立即重试
            redisUtils.delete(key);
            throw e;
        }
    }

    /** C20d：已登录按 userId 隔离，匿名按来源 IP 隔离 */
    private String resolveSubject() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            return "u" + userId;
        }
        String ip = "anon";
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            if (request != null) {
                ip = JakartaServletUtil.getClientIP(request);
            }
        }
        return "ip:" + ip;
    }

    private String buildParamString(Object[] args) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                sb.append(arg);
            }
            return sb.toString();
        }
    }
}

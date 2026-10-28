package com.dianfei.common.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.log.OperLogRecord;
import com.dianfei.common.log.OperLogStorage;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志切面：记录操作人/方法/参数/耗时/IP，经 OperLogStorage 异步落库
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_TEXT_LENGTH = 2000;

    /** C10 安全加固：参数序列化使用脱敏 mapper（密码/验证码/身份证/令牌等一律 ***，不落日志） */
    private static final com.fasterxml.jackson.databind.ObjectMapper MASKED_MAPPER =
            com.dianfei.common.utils.SensitiveLogMask.maskedMapper();

    private final ObjectProvider<OperLogStorage> storageProvider;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            saveLog(joinPoint, operLog, result, error, System.currentTimeMillis() - start);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLog,
                         Object result, Throwable error, long costMs) {
        try {
            OperLogStorage storage = storageProvider.getIfAvailable();
            if (storage == null) {
                return;
            }
            LoginUser user = UserContext.get();
            OperLogRecord record = OperLogRecord.builder()
                    .module(operLog.module())
                    .operation(operLog.value())
                    .method(joinPoint.getSignature().toShortString())
                    .params(operLog.saveParams() ? toJson(joinPoint.getArgs()) : null)
                    .result(error == null ? StrUtil.subPre(toJson(result), MAX_TEXT_LENGTH) : null)
                    .status(error == null ? 0 : 1)
                    .errorMsg(error == null ? null : StrUtil.subPre(error.getMessage(), MAX_TEXT_LENGTH))
                    .costMs(costMs)
                    .operId(user == null ? null : user.getUserId())
                    .operName(user == null ? "anonymous" : user.getUsername())
                    .principalType(user == null ? null : user.getPrincipalType())
                    .operIp(getClientIp())
                    .operTime(LocalDateTime.now())
                    .build();
            storage.save(record);
        } catch (Exception e) {
            log.warn("[操作日志] 记录失败，不影响主流程", e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return StrUtil.subPre(MASKED_MAPPER.writeValueAsString(obj), MAX_TEXT_LENGTH);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return JakartaServletUtil.getClientIP(request);
    }
}

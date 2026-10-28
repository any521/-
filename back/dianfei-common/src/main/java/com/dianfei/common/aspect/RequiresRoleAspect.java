package com.dianfei.common.aspect;

import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色校验切面：方法级注解优先于类级
 */
@Aspect
@Component
public class RequiresRoleAspect {

    @Before("@annotation(com.dianfei.common.annotation.RequiresRole) "
            + "|| @within(com.dianfei.common.annotation.RequiresRole)")
    public void checkRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequiresRole annotation = signature.getMethod().getAnnotation(RequiresRole.class);
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequiresRole.class);
        }
        if (annotation == null) {
            return;
        }
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        boolean allowed = Arrays.asList(annotation.value()).contains(user.getRole());
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}

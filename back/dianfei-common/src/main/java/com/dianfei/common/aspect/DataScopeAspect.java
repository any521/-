package com.dianfei.common.aspect;

import com.dianfei.common.annotation.DataScope;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 片区数据权限切面：ADMIN 不过滤；其他角色按 UserContext.areaId 生成过滤 SQL 片段，
 * 写入 DataScopeContext（ThreadLocal），Mapper XML 以 ${ew} 或自定义拦截拼接使用
 */
@Aspect
@Component
public class DataScopeAspect {

    private static final String ROLE_ADMIN = "ADMIN";

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        try {
            LoginUser user = UserContext.get();
            if (user != null && !ROLE_ADMIN.equals(user.getRole()) && user.getAreaId() != null) {
                String column = dataScope.tableAlias().isEmpty()
                        ? dataScope.areaColumn()
                        : dataScope.tableAlias() + "." + dataScope.areaColumn();
                DataScopeContext.set(column + " = " + user.getAreaId());
            }
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    /**
     * 数据权限 SQL 片段上下文：Service/Mapper 在 @DataScope 方法内可取到过滤条件
     */
    public static class DataScopeContext {

        private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

        public static void set(String sqlFilter) {
            HOLDER.set(sqlFilter);
        }

        /** 返回过滤片段，如 "o.area_id = 3"；无限制时返回 null */
        public static String get() {
            return HOLDER.get();
        }

        public static void clear() {
            HOLDER.remove();
        }
    }
}

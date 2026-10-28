package com.dianfei.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志：AOP 记录操作人/方法/参数/耗时/IP，异步落库不阻塞主流程
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 操作说明，如 "新增角色" */
    String value() default "";

    /** 业务模块，如 "系统管理" */
    String module() default "";

    /** 是否记录请求参数 */
    boolean saveParams() default true;
}

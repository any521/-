package com.dianfei.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 接口幂等：Redis SETNX 防重（对齐 04.7）
 * 覆盖 MCP 下单、Agent 执行、语音下单等机器发起调用，防止重试导致重复订单/积分
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 幂等键前缀，默认取 类名:方法名 */
    String prefix() default "";

    /** 幂等窗口时长 */
    long timeout() default 5;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 重复提交时的提示 */
    String message() default "请勿重复提交";
}

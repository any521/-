package com.dianfei.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 片区数据权限：员工仅见本片区数据（对齐 memory.md 权限体系）
 * 切面读取 UserContext.areaId 生成过滤条件，ADMIN 角色不过滤
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 片区字段所在表别名，如 "o"（生成 o.area_id = xxx） */
    String tableAlias() default "";

    /** 片区字段名 */
    String areaColumn() default "area_id";
}

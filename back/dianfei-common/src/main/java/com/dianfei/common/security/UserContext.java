package com.dianfei.common.security;

/**
 * 当前登录用户上下文（ThreadLocal，由 JwtAuthFilter 填充、请求结束清理）
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static String getRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    public static Long getAreaId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getAreaId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

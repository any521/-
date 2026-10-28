package com.dianfei.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码（对齐 12.3 错误码表）
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "成功"),
    FAILED(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或 Token 已过期"),
    FORBIDDEN(403, "无权限"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    /** 业务错误 2xxx */
    ORDER_STATUS_NOT_ALLOWED(2003, "订单状态不允许该操作"),
    POINTS_NOT_ENOUGH(2006, "积分不足"),

    /** 资源不存在（地址簿/评论等按 id 归属校验） */
    NOT_FOUND(4004, "资源不存在"),

    /** AI 错误 3xxx */
    AI_SERVICE_UNAVAILABLE(3001, "AI 服务不可用"),
    AI_OUTPUT_INVALID(3002, "结构化输出校验失败"),

    /** 系统错误 5xxx */
    SYSTEM_ERROR(5000, "系统异常"),
    /** onsite04：地图服务不可用（高德失败/未配置 Key，前端 Toast 降级） */
    MAP_SERVICE_UNAVAILABLE(5001, "地图服务不可用");

    private final Integer code;
    private final String msg;

    /**
     * 语义化 HTTP 状态码（C20）：认证/授权/限流/参数错误返回真实状态码，便于网关/监控/抓包识别；
     * 其余业务码（2xxx/3xxx/5xxx）保持 HTTP 200 + body code 承载，前端统一按 body 处理
     */
    public int httpStatus() {
        return switch (this) {
            case UNAUTHORIZED -> 401;
            case FORBIDDEN -> 403;
            case TOO_MANY_REQUESTS -> 429;
            case PARAM_ERROR -> 400;
            default -> 200;
        };
    }
}

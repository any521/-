package com.dianfei.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 回收订单状态机：0待接单 → 1已接单 → 2进行中 → 3已完成 / 4已取消 / 5异常
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING(0, "待接单"),
    ACCEPTED(1, "已接单"),
    IN_PROGRESS(2, "进行中"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消"),
    ABNORMAL(5, "异常");

    private final Integer code;
    private final String desc;

    public static OrderStatusEnum of(Integer code) {
        for (OrderStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}

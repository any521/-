package com.dianfei.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 维修工单状态机（11.2）：
 * 0待接单 → 1已接单 → 2检修中 → 3已完成(可修复) / 5已转回收(不可修复联动)
 * 0/1 可取消 → 4已取消
 */
@Getter
@AllArgsConstructor
public enum RepairStatusEnum {

    PENDING(0, "待接单"),
    ACCEPTED(1, "已接单"),
    CHECKING(2, "检修中"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消"),
    TRANSFERRED(5, "已转回收");

    private final Integer code;
    private final String desc;

    public static RepairStatusEnum of(Integer code) {
        for (RepairStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}

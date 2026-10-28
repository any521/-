package com.dianfei.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 三类员工：再生资源回收员 / 环卫收运员 / 电路维修技术员
 */
@Getter
@AllArgsConstructor
public enum StaffTypeEnum {

    RECYCLER(1, "再生资源回收员"),
    SANITATION(2, "环卫收运员"),
    TECHNICIAN(3, "电路维修技术员");

    private final Integer code;
    private final String desc;

    public static StaffTypeEnum of(Integer code) {
        for (StaffTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}

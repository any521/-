package com.dianfei.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 碳流水类型（10.2：1获得 2消费 3补发 4扣减 5清零；6冻结 7解冻为 B2 积分调整扩展；
 * 12转出 13转入为 onsite02 服务费转账扩展，数值区间 12/13 预留不与既有值撞号）
 */
@Getter
@AllArgsConstructor
public enum CarbonLogTypeEnum {

    GAIN(1, "获得"),
    CONSUME(2, "消费"),
    REISSUE(3, "补发"),
    DEDUCT(4, "扣减"),
    CLEAR(5, "清零"),
    FREEZE(6, "冻结"),
    UNFREEZE(7, "解冻"),
    TRANSFER_OUT(12, "积分转出（支付服务费）"),
    TRANSFER_IN(13, "积分转入（服务费收入）");

    private final int code;
    private final String desc;

    public static CarbonLogTypeEnum of(int code) {
        for (CarbonLogTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}

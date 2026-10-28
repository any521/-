package com.dianfei.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单类型：碳流水 order_type / 售后 order_type 共用（对齐 10.2 biz_carbon_log）
 */
@Getter
@AllArgsConstructor
public enum OrderTypeEnum {

    RECYCLE("RECYCLE", "回收订单"),
    KITCHEN("KITCHEN", "垃圾投放"),
    REPAIR("REPAIR", "维修工单"),
    MALL("MALL", "商城兑换"),
    FLEA("FLEA", "跳蚤市场"),
    BADGE("BADGE", "徽章兑换"), // 积分市场合并升级：成就徽章积分兑换扣分
    SCIENCE("SCIENCE", "科普下载"); // V42: 科普专栏下载积分消费

    private final String code;
    private final String desc;

    public static OrderTypeEnum of(String code) {
        for (OrderTypeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        return null;
    }
}

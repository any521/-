package com.dianfei.common.enums;

/**
 * 多端识别（Header: X-Client-Type）
 */
public enum ClientTypeEnum {

    USER,
    STAFF,
    ADMIN;

    /** 多端识别请求头名 */
    public static final String HEADER = "X-Client-Type";

    public static ClientTypeEnum of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

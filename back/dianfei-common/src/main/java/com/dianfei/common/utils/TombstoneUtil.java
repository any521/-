package com.dianfei.common.utils;

/**
 * 逻辑删除墓碑工具：删除前将唯一键字段改写为 "{原值}#D{id}"（超长截断原值保住列宽），
 * 释放唯一索引占用使同码可重建；后缀携带记录 id 保留追溯（对齐 D7 归档要求）。
 */
public final class TombstoneUtil {

    private TombstoneUtil() {
    }

    /**
     * @param value  唯一键原值（NOT NULL 列）
     * @param id     记录主键，进入后缀用于追溯
     * @param maxLen 列定义长度，结果保证不超过该长度
     */
    public static String mark(String value, Long id, int maxLen) {
        String suffix = "#D" + id;
        int keep = Math.max(1, maxLen - suffix.length());
        String base = value.length() > keep ? value.substring(0, keep) : value;
        return base + suffix;
    }
}

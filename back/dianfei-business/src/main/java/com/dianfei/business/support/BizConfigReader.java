package com.dianfei.business.support;

import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 业务参数读取（sys_config 热更新）：
 * 委托 system 的 ConfigService（Redis 缓存→DB 回填）；轻量单测上下文无该 Bean 时回退默认值，
 * 保证业务闭环不因配置设施缺席而中断（对齐 D15 降级优先）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BizConfigReader {

    /** 垃圾投放开始时段（时） */
    public static final String KEY_KITCHEN_REPORT_START = "kitchen.report.start-hour";
    /** 垃圾投放结束时段（时） */
    public static final String KEY_KITCHEN_REPORT_END = "kitchen.report.end-hour";
    /** 垃圾投放上报偏离片区户均告警倍数 */
    public static final String KEY_KITCHEN_DEVIATION_TIMES = "risk.kitchen.deviation-times";

    private final ObjectProvider<ConfigService> configServiceProvider;

    public int getInt(String key, int defaultValue) {
        ConfigService configService = configServiceProvider.getIfAvailable();
        if (configService == null) {
            return defaultValue;
        }
        String value = configService.getValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[Config] {} 值 {} 非整数，回退默认 {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}

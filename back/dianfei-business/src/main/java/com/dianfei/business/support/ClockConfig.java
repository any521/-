package com.dianfei.business.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 业务时钟（B4 垃圾投放 20-23 点时段校验等依赖系统时间的逻辑统一注入 Clock）：
 * 生产用系统时钟；单测提供固定/可拨动 Clock 覆盖，时段规则可确定性验证。
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock businessClock() {
        return Clock.systemDefaultZone();
    }
}

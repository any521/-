package com.dianfei.business;

import com.dianfei.common.utils.JwtUtils;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.system.service.ConfigService;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 闭环单测支撑配置（C4 修复）：
 * ScanPointsService 等新业务注入 system 模块 ConfigService 与 common RedisUtils，
 * 测试上下文（H2，无 Redis）以 mock 提供，保证 @SpringBootTest 可启动。
 * getValue(key, default) 恒返回 defaultValue，避免默认 null 在业务内 NPE。
 */
@Configuration
public class TestSupportConfig {

    @Bean
    public ConfigService configService() {
        ConfigService mock = Mockito.mock(ConfigService.class);
        Mockito.when(mock.getValue(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        return mock;
    }

    @Bean
    public RedisUtils redisUtils() {
        return Mockito.mock(RedisUtils.class);
    }

    @Bean
    public JwtUtils jwtUtils() {
        return Mockito.mock(JwtUtils.class);
    }
}

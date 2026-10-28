package com.dianfei.business.carbon;

import com.dianfei.business.TestSupportConfig;
import com.dianfei.common.mybatis.DianfeiMetaObjectHandler;
import com.dianfei.common.mybatis.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * carbon 域闭环单测引导：装配 carbon 包 + MP 配置/自动填充，
 * H2（MySQL 兼容模式）跑账本闭环，无需本地 MySQL/Redis（对齐 trackB 并行说明）。
 * TestSupportConfig 提供 mock（carbon 包内的 controller/service 注入的 ConfigService/RedisUtils）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dianfei.business",
        "com.dianfei.common.mybatis"
}, exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        RedisAutoConfiguration.class})
@MapperScan({
        "com.dianfei.system.mapper",
        "com.dianfei.business.support",
        "com.dianfei.business.aftersale.mapper",
        "com.dianfei.business.agent.mapper",
        "com.dianfei.business.carbon.mapper",
        "com.dianfei.business.dashboard.mapper",
        "com.dianfei.business.diy.mapper",
        "com.dianfei.business.eco.mapper",
        "com.dianfei.business.evaluate.mapper",
        "com.dianfei.business.flea.mapper",
        "com.dianfei.business.home.mapper",
        "com.dianfei.business.im.mapper",
        "com.dianfei.business.kitchen.mapper",
        "com.dianfei.business.mall.mapper",
        "com.dianfei.business.map.mapper",
        "com.dianfei.business.message.mapper",
        "com.dianfei.business.ops.mapper",
        "com.dianfei.business.recycle.mapper",
        "com.dianfei.business.repair.mapper",
        "com.dianfei.business.repair.linkage.mapper",
        "com.dianfei.business.risk.mapper",
        "com.dianfei.business.staff.mapper"
})
@Import({MybatisPlusConfig.class, DianfeiMetaObjectHandler.class, TestSupportConfig.class})
public class CarbonTestApplication {
}

package com.dianfei.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 单测专用启动类（H2）：扫业务包 + common 的 MyBatis 配置（分页/自动填充），
 * 不拉起鉴权/Redis/Web 全量骨架，保证闭环单测轻量可跑。
 * TestSupportConfig 提供 mock（ScanPointsService 注入的 ConfigService/RedisUtils）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.dianfei.business",
        "com.dianfei.common.mybatis"
})
@MapperScan({
        "com.dianfei.system.mapper",
        "com.dianfei.business.support",
        "com.dianfei.business.addressbook.mapper",
        "com.dianfei.business.aftersale.mapper",
        "com.dianfei.business.agent.mapper",
        "com.dianfei.business.badge.mapper",
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
        "com.dianfei.business.science.mapper",
        "com.dianfei.business.staff.mapper"
})
@Import(TestSupportConfig.class)
public class BusinessTestApplication {
}

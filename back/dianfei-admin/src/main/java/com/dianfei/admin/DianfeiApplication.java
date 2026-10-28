package com.dianfei.admin;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 点废成金 v2.0 启动类（聚合全部模块）
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.dianfei")
@MapperScan({"com.dianfei.**.mapper", "com.dianfei.business.support"})
@EnableAsync
@EnableScheduling
public class DianfeiApplication {

    public static void main(String[] args) {
        // W-05: 启动横幅改用 log.info 输出（替换原 System.out.println），避免标准输出绕过日志框架
        SpringApplication app = new SpringApplication(DianfeiApplication.class);
        app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> printStartupBanner(event.getApplicationContext().getEnvironment()));
        app.run(args);
    }

    /**
     * 输出启动横幅信息。端口动态读取自 ${server.port}（默认 8080，仅为占位回退值）。
     */
    private static void printStartupBanner(Environment env) {
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        log.info("------------------------------------------------");
        log.info("  点废成金 dianfei-backend v2.0 启动成功");
        log.info("  Knife4j: http://localhost:{}{}doc.html", port, "/".equals(contextPath) ? "/" : contextPath + "/");
        log.info("------------------------------------------------");
    }
}

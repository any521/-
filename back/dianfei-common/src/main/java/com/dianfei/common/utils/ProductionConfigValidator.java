package com.dianfei.common.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 生产配置 fail-closed 校验。Compose 的必填变量校验只能覆盖容器启动，
 * 这里覆盖裸 JVM、Kubernetes 和手工启动场景。
 */
@Component
public class ProductionConfigValidator {

    private final String profile;
    private final String datasourcePassword;
    private final String redisPassword;
    private final String mailPassword;
    private final String internalToken;
    private final String callbackToken;
    private final String configAesKey;
    private final String corsOrigins;

    public ProductionConfigValidator(
            @Value("${spring.profiles.active:}") String profile,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${dianfei.ai.internal-token:}") String internalToken,
            @Value("${dianfei.ai.config-callback-token:}") String callbackToken,
            @Value("${dianfei.ai.config-aes-key:}") String configAesKey,
            @Value("${dianfei.cors.allowed-origins:}") String corsOrigins) {
        this.profile = profile == null ? "" : profile;
        this.datasourcePassword = datasourcePassword;
        this.redisPassword = redisPassword;
        this.mailPassword = mailPassword;
        this.internalToken = internalToken;
        this.callbackToken = callbackToken;
        this.configAesKey = configAesKey;
        this.corsOrigins = corsOrigins;
    }

    @PostConstruct
    void validate() {
        if (!profile.contains("prod")) {
            return;
        }
        require("spring.datasource.password", datasourcePassword);
        require("spring.data.redis.password", redisPassword);
        // S1：邮件授权码同样禁止缺省（曾依赖仓库硬编码默认值，已移除）
        require("spring.mail.password", mailPassword);
        requireStrong("dianfei.ai.internal-token", internalToken);
        requireStrong("dianfei.ai.config-callback-token", callbackToken);
        require("dianfei.ai.config-aes-key", configAesKey);
        require("dianfei.cors.allowed-origins", corsOrigins);
        int keyBytes = configAesKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (keyBytes != 16 && keyBytes != 24 && keyBytes != 32) {
            throw new IllegalStateException("dianfei.ai.config-aes-key 必须为 16/24/32 字节");
        }
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("生产环境必须配置 " + name);
        }
    }

    private static void requireStrong(String name, String value) {
        require(name, value);
        if (value.trim().length() < 32) {
            throw new IllegalStateException(name + " 长度不能少于 32 个字符");
        }
    }
}

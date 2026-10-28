package com.dianfei.common.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 敏感字段加密工具（C10 安全加固）
 * <p>身份证等敏感字段入库前加密（AES/ECB/PKCS5Padding），
 * 密钥从环境变量 DIANFEI_FIELD_KEY（dianfei.security.field-key）注入；
 * 未配置密钥时降级为透传（仅限本地开发，生产必须配置强随机 32 字节密钥）。
 */
@Component
public class SensitiveFieldUtil {

    private static final String MASK_PREFIX = "enc:";

    private final String fieldKey;
    private final String activeProfiles;

    public SensitiveFieldUtil(@Value("${dianfei.security.field-key:}") String fieldKey,
                              @Value("${spring.profiles.active:}") String activeProfiles) {
        this.fieldKey = StrUtil.trimToNull(fieldKey);
        this.activeProfiles = StrUtil.nullToEmpty(activeProfiles);
    }

    @PostConstruct
    void validateConfiguration() {
        if (fieldKey == null && activeProfiles.contains("prod")) {
            throw new IllegalStateException("生产环境必须配置 dianfei.security.field-key/DIANFEI_FIELD_KEY");
        }
        if (fieldKey != null && !Set.of(16, 24, 32).contains(fieldKey.getBytes(StandardCharsets.UTF_8).length)) {
            throw new IllegalStateException("dianfei.security.field-key 必须为 16/24/32 字节");
        }
    }

    /** 加密：明文 → enc:Base64 密文（key 未配置时透传原值） */
    public String encrypt(String plain) {
        if (StrUtil.isBlank(plain)) {
            return plain;
        }
        if (fieldKey == null) {
            return plain;
        }
        AES aes = new AES(fieldKey.getBytes(StandardCharsets.UTF_8));
        return MASK_PREFIX + aes.encryptBase64(plain);
    }

    /** 解密：enc:Base64 密文 → 明文（非加密值原样返回，兼容历史明文数据） */
    public String decrypt(String cipher) {
        if (StrUtil.isBlank(cipher) || !cipher.startsWith(MASK_PREFIX)) {
            return cipher;
        }
        if (fieldKey == null) {
            return cipher;
        }
        try {
            AES aes = new AES(fieldKey.getBytes(StandardCharsets.UTF_8));
            return aes.decryptStr(cipher.substring(MASK_PREFIX.length()));
        } catch (Exception e) {
            // 密钥变更等异常：返回原值兜底，避免业务中断
            return cipher;
        }
    }

    /** 是否已启用加密（生产部署应确保返回 true） */
    public boolean isEnabled() {
        return fieldKey != null;
    }
}

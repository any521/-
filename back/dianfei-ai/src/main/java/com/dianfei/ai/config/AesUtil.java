package com.dianfei.ai.config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加解密工具（密钥列存储用）
 * <p>算法：AES/ECB/PKCS5Padding；密钥从 dianfei.ai.config-aes-key 读取，
 * 需为 16/24/32 字节。空密钥时降级为透传（开发环境，生产务必配置）。
 */
public final class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private AesUtil() {
    }

    /**
     * 加密明文为 Base64 字符串
     *
     * @param plainText 明文
     * @param aesKey    AES 密钥；null/空时透传返回明文（开发降级）
     */
    public static String encrypt(String plainText, String aesKey) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        if (aesKey == null || aesKey.isEmpty()) {
            return plainText;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fixKey(aesKey), ALGORITHM));
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密 Base64 密文
     *
     * @param cipherText 密文
     * @param aesKey     AES 密钥；null/空时透传（开发降级）
     */
    public static String decrypt(String cipherText, String aesKey) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (aesKey == null || aesKey.isEmpty()) {
            return cipherText;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(fixKey(aesKey), ALGORITHM));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 密钥变更或历史明文数据：返回原文兜底，避免业务中断
            return cipherText;
        }
    }

    /** 密钥长度补齐到 16/24/32 字节（按 UTF-8 字节截断或补零） */
    private static byte[] fixKey(String aesKey) {
        byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
        int len = raw.length >= 32 ? 32 : (raw.length >= 24 ? 24 : 16);
        byte[] fixed = new byte[len];
        System.arraycopy(raw, 0, fixed, 0, Math.min(raw.length, len));
        return fixed;
    }
}

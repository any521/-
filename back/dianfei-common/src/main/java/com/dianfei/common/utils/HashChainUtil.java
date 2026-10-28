package com.dianfei.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 哈希链工具：规范化 JSON → SHA-256 逐条链式哈希（对齐 04.7 / D16）
 * curr_hash = SHA256(prev_hash + canonicalJson(payload))
 */
public final class HashChainUtil {

    /** 创世哈希：链上第一条记录的 prev_hash */
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private HashChainUtil() {
    }

    /** 对象 → 规范化 JSON（键按字典序排序，保证同一数据哈希稳定） */
    public static String canonicalJson(Object payload) {
        try {
            return CANONICAL_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload 无法序列化为规范化 JSON", e);
        }
    }

    /** SHA-256，输出 64 位小写十六进制 */
    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 链式哈希：curr = SHA256(prevHash + canonicalJson(payload)) */
    public static String chainHash(String prevHash, Object payload) {
        String prev = (prevHash == null || prevHash.isBlank()) ? GENESIS_HASH : prevHash;
        return sha256Hex(prev + canonicalJson(payload));
    }

    /** 校验单条链节点 */
    public static boolean verify(String prevHash, Object payload, String expectedHash) {
        return chainHash(prevHash, payload).equals(expectedHash);
    }
}

package com.dianfei.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 哈希链工具测试向量（04.7 要求附完整单测向量）
 */
class HashChainUtilTest {

    @Test
    void sha256_knownVector() {
        // NIST 标准向量：SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashChainUtil.sha256Hex("abc"));
    }

    @Test
    void canonicalJson_keyOrderStable() {
        String a = HashChainUtil.canonicalJson(Map.of("b", 2, "a", 1));
        String b = HashChainUtil.canonicalJson(Map.of("a", 1, "b", 2));
        assertEquals(a, b);
        assertEquals("{\"a\":1,\"b\":2}", a);
    }

    @Test
    void chainHash_genesisAndLink() {
        Map<String, Object> p1 = Map.of("points", 10, "orderId", 1L);
        Map<String, Object> p2 = Map.of("points", 20, "orderId", 2L);
        String h1 = HashChainUtil.chainHash(null, p1);
        String h2 = HashChainUtil.chainHash(h1, p2);
        // 创世等价于显式 GENESIS_HASH
        assertEquals(HashChainUtil.chainHash(HashChainUtil.GENESIS_HASH, p1), h1);
        // 篡改 p1 后链断裂
        String tampered = HashChainUtil.chainHash(null, Map.of("points", 999, "orderId", 1L));
        assertNotEquals(h1, tampered);
        assertNotEquals(h2, HashChainUtil.chainHash(tampered, p2));
        assertEquals(64, h1.length());
    }
}

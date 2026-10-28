package com.dianfei.common.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerkleTreeUtilTest {

    private List<String> leaves(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> HashChainUtil.sha256Hex("leaf-" + i))
                .toList();
    }

    @Test
    void buildRoot_singleLeafIsItself() {
        List<String> ls = leaves(1);
        assertEquals(ls.get(0), MerkleTreeUtil.buildRoot(ls));
    }

    @Test
    void buildRoot_emptyIsZero() {
        assertEquals("0".repeat(64), MerkleTreeUtil.buildRoot(List.of()));
    }

    @Test
    void proof_verifiesForEveryLeaf_evenAndOddCounts() {
        for (int n : new int[]{2, 3, 5, 8, 13}) {
            List<String> ls = leaves(n);
            String root = MerkleTreeUtil.buildRoot(ls);
            for (int i = 0; i < n; i++) {
                var proof = MerkleTreeUtil.buildProof(ls, i);
                assertTrue(MerkleTreeUtil.verifyProof(ls.get(i), proof, root),
                        "n=" + n + ", leaf=" + i + " 验证失败");
            }
        }
    }

    @Test
    void proof_failsOnTamperedLeaf() {
        List<String> ls = leaves(5);
        String root = MerkleTreeUtil.buildRoot(ls);
        var proof = MerkleTreeUtil.buildProof(ls, 2);
        String fakeLeaf = HashChainUtil.sha256Hex("tampered");
        assertFalse(MerkleTreeUtil.verifyProof(fakeLeaf, proof, root));
    }
}

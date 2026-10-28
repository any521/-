package com.dianfei.common.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkle 树工具：批量叶子 → Merkle 根与验证路径（每日锚定，对齐 04.7 / D16）
 * 奇数节点时最后一个节点与自身配对；节点哈希 = SHA256(left + right)
 */
public final class MerkleTreeUtil {

    private MerkleTreeUtil() {
    }

    /** 验证路径节点：兄弟哈希 + 方向（true=兄弟在左） */
    public record ProofNode(String hash, boolean isLeft) {
    }

    /** 计算 Merkle 根；空列表返回全零哈希 */
    public static String buildRoot(List<String> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            return HashChainUtil.GENESIS_HASH;
        }
        List<String> level = new ArrayList<>(leafHashes);
        while (level.size() > 1) {
            level = buildParentLevel(level);
        }
        return level.get(0);
    }

    /** 生成指定叶子的 Merkle 验证路径 */
    public static List<ProofNode> buildProof(List<String> leafHashes, int leafIndex) {
        if (leafHashes == null || leafIndex < 0 || leafIndex >= leafHashes.size()) {
            throw new IllegalArgumentException("叶子索引越界");
        }
        List<ProofNode> proof = new ArrayList<>();
        List<String> level = new ArrayList<>(leafHashes);
        int index = leafIndex;
        while (level.size() > 1) {
            int siblingIndex = (index % 2 == 0) ? index + 1 : index - 1;
            // 奇数层末尾节点与自身配对
            if (siblingIndex >= level.size()) {
                siblingIndex = index;
            }
            proof.add(new ProofNode(level.get(siblingIndex), siblingIndex < index));
            level = buildParentLevel(level);
            index = index / 2;
        }
        return proof;
    }

    /** 按验证路径重算根并比对 */
    public static boolean verifyProof(String leafHash, List<ProofNode> proof, String expectedRoot) {
        String current = leafHash;
        for (ProofNode node : proof) {
            current = node.isLeft()
                    ? HashChainUtil.sha256Hex(node.hash() + current)
                    : HashChainUtil.sha256Hex(current + node.hash());
        }
        return current.equals(expectedRoot);
    }

    private static List<String> buildParentLevel(List<String> level) {
        List<String> parent = new ArrayList<>((level.size() + 1) / 2);
        for (int i = 0; i < level.size(); i += 2) {
            String left = level.get(i);
            String right = (i + 1 < level.size()) ? level.get(i + 1) : left;
            parent.add(HashChainUtil.sha256Hex(left + right));
        }
        return parent;
    }
}

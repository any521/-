package com.dianfei.business.carbon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.CarbonLedgerServiceImpl;
import com.dianfei.business.carbon.entity.CarbonAnchor;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonAnchorMapper;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.vo.CarbonVerifyVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.utils.HashChainUtil;
import com.dianfei.common.utils.MerkleTreeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 碳流水可信验证（B2/F3）：单条哈希重算 + 账户链衔接校验 + Merkle 路径验证到每日锚定根
 */
@Service
@RequiredArgsConstructor
public class CarbonVerifyService {

    private final CarbonLogMapper logMapper;
    private final CarbonAnchorMapper anchorMapper;
    private final CarbonAccountMapper accountMapper;

    /** MCP/用户侧证书查询必须先验证流水所属账户。 */
    public CarbonVerifyVO verifyOwned(Long logId, Long userId) {
        CarbonLog row = logMapper.selectById(logId);
        if (row == null) {
            throw new BusinessException("流水不存在");
        }
        var account = accountMapper.selectById(row.getAccountId());
        if (account == null || !userId.equals(account.getUserId())) {
            throw new BusinessException(com.dianfei.common.result.ResultCode.FORBIDDEN, "无权查看该流水");
        }
        return verify(logId);
    }

    public CarbonVerifyVO verify(Long logId) {
        CarbonLog row = logMapper.selectById(logId);
        if (row == null) {
            throw new BusinessException("流水不存在");
        }
        // 1) payload 重算：篡改任一业务字段或 curr_hash 即失败
        boolean hashValid = HashChainUtil.verify(row.getPrevHash(),
                CarbonLedgerServiceImpl.hashPayload(row), row.getCurrHash());
        // 2) 链衔接：prev_hash 必须等于账户内前一条的 curr_hash（首条为 GENESIS）
        CarbonLog prev = logMapper.selectOne(new LambdaQueryWrapper<CarbonLog>()
                .eq(CarbonLog::getAccountId, row.getAccountId())
                .lt(CarbonLog::getId, row.getId())
                .orderByDesc(CarbonLog::getId)
                .last("LIMIT 1"));
        boolean chainLinked = prev == null
                ? HashChainUtil.GENESIS_HASH.equals(row.getPrevHash())
                : prev.getCurrHash().equals(row.getPrevHash());

        CarbonVerifyVO.CarbonVerifyVOBuilder builder = CarbonVerifyVO.builder()
                .logId(row.getId())
                .hashValid(hashValid)
                .chainLinked(chainLinked)
                .anchored(false);

        // 3) Merkle 路径验证到锚定根
        Boolean rootMatched = null;
        if (row.getAnchorId() != null) {
            CarbonAnchor anchor = anchorMapper.selectById(row.getAnchorId());
            if (anchor != null) {
                List<CarbonLog> dayLogs = logMapper.selectList(new LambdaQueryWrapper<CarbonLog>()
                        .eq(CarbonLog::getAnchorId, anchor.getId())
                        .orderByAsc(CarbonLog::getId));
                List<String> leaves = dayLogs.stream().map(CarbonLog::getCurrHash).toList();
                int index = indexOf(dayLogs, row.getId());
                List<MerkleTreeUtil.ProofNode> proof = MerkleTreeUtil.buildProof(leaves, index);
                rootMatched = MerkleTreeUtil.verifyProof(row.getCurrHash(), proof, anchor.getMerkleRoot());
                builder.anchored(true)
                        .anchorDate(anchor.getAnchorDate())
                        .merkleRoot(anchor.getMerkleRoot())
                        .rootMatched(rootMatched)
                        .proof(proof.stream()
                                .map(n -> CarbonVerifyVO.ProofNodeVO.builder()
                                        .hash(n.hash()).left(n.isLeft()).build())
                                .toList());
            }
        }
        boolean valid = hashValid && chainLinked && (rootMatched == null || rootMatched);
        return builder.valid(valid).build();
    }

    private int indexOf(List<CarbonLog> logs, Long logId) {
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).getId().equals(logId)) {
                return i;
            }
        }
        throw new BusinessException("流水未包含在锚定叶子中");
    }
}

package com.dianfei.business.carbon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dianfei.business.carbon.entity.CarbonAnchor;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonAnchorMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.common.utils.MerkleTreeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日 Merkle 锚定（B2）：聚合当日全部碳流水 curr_hash 为叶子构树，
 * 根写入 biz_carbon_anchor 并回填流水 anchor_id（该列为锚定账号唯一可更新列，对齐 04.8）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonAnchorService {

    private final CarbonLogMapper logMapper;
    private final CarbonAnchorMapper anchorMapper;

    /** 对指定日期执行锚定（幂等：当日已锚定则直接返回既有记录；无流水返回 null） */
    @Transactional
    public CarbonAnchor anchorFor(LocalDate date) {
        CarbonAnchor existing = anchorMapper.selectOne(
                new LambdaQueryWrapper<CarbonAnchor>().eq(CarbonAnchor::getAnchorDate, date));
        if (existing != null) {
            log.info("[CarbonAnchor] {} 已锚定 root={}", date, existing.getMerkleRoot());
            return existing;
        }
        List<CarbonLog> logs = logMapper.selectList(new LambdaQueryWrapper<CarbonLog>()
                .ge(CarbonLog::getCreateTime, date.atStartOfDay())
                .lt(CarbonLog::getCreateTime, date.plusDays(1).atStartOfDay())
                .orderByAsc(CarbonLog::getId));
        if (logs.isEmpty()) {
            log.info("[CarbonAnchor] {} 无流水，跳过锚定", date);
            return null;
        }
        List<String> leaves = logs.stream().map(CarbonLog::getCurrHash).toList();
        CarbonAnchor anchor = new CarbonAnchor();
        anchor.setAnchorDate(date);
        anchor.setMerkleRoot(MerkleTreeUtil.buildRoot(leaves));
        anchor.setLeafCount(leaves.size());
        // 外部存证（区块链/公证处）为可选增强，接入后回写 external_ref
        anchorMapper.insert(anchor);
        logMapper.update(null, new LambdaUpdateWrapper<CarbonLog>()
                .set(CarbonLog::getAnchorId, anchor.getId())
                .in(CarbonLog::getId, logs.stream().map(CarbonLog::getId).toList()));
        log.info("[CarbonAnchor] {} 锚定完成 leafCount={} root={}", date, leaves.size(), anchor.getMerkleRoot());
        return anchor;
    }
}

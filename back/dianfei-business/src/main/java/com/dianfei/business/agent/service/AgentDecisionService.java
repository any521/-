package com.dianfei.business.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.business.agent.dto.DecisionSubmitDTO;
import com.dianfei.business.agent.entity.AgentDecision;
import com.dianfei.business.agent.mapper.AgentDecisionMapper;
import com.dianfei.business.agent.vo.AgentCockpitVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.HashChainUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Agent 决策单服务（C5 / F8，对齐 06.2 与 D17，全平台唯一实现）：
 * 提交时按风险等级路由——1低自动执行 / 2中运营单人审批 / 3高双人复核；
 * 决策内容全量入链（prev/decision hash 全表逐条链接，参与每日 Merkle 锚定）；
 * HITL 审批回写状态与审批人，通过后由 {@link DecisionExecutor} 代为执行并回写结果。
 * AI 模块（Track E）通过 {@link #submitBatch} 批量落库运行产出的决策草稿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDecisionService {

    private final AgentDecisionMapper decisionMapper;
    private final DecisionExecutor decisionExecutor;

    /**
     * Agent 提交决策单：入链哈希 → 低风险自动执行，中/高风险进待审批队列。
     * synchronized 保证单节点下链尾读写原子（MVP 单库；集群化后改为 DB 行锁/序列化事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized AgentDecision submit(DecisionSubmitDTO dto) {
        return saveDecision(dto, false);
    }

    /**
     * AI 模块（Track E F8）批量落库运行产出的决策草稿：
     * 统一以运行主体 agentId 落库，dryRun=true 时低风险也不自动执行（仅产出决策单）。
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized List<AgentDecision> submitBatch(String agentId, List<DecisionSubmitDTO> drafts,
                                                        boolean dryRun) {
        List<AgentDecision> saved = new ArrayList<>();
        for (DecisionSubmitDTO dto : drafts) {
            dto.setAgentId(agentId);
            saved.add(saveDecision(dto, dryRun));
        }
        return saved;
    }

    /** 单条决策落库：入链哈希 → 低风险（且非 dryRun）自动执行 */
    private AgentDecision saveDecision(DecisionSubmitDTO dto, boolean dryRun) {
        AgentDecision decision = new AgentDecision();
        decision.setAgentId(dto.getAgentId());
        decision.setActionType(dto.getActionType());
        decision.setTargetUri(dto.getTargetUri());
        decision.setParamsJson(dto.getParamsJson());
        decision.setEvidencesJson(dto.getEvidencesJson());
        decision.setRiskLevel(dto.getRiskLevel());
        decision.setStatus(AgentDecision.STATUS_PENDING);

        AgentDecision last = decisionMapper.selectOne(new LambdaQueryWrapper<AgentDecision>()
                .orderByDesc(AgentDecision::getId).last("LIMIT 1"));
        decision.setPrevHash(last == null ? HashChainUtil.GENESIS_HASH : last.getDecisionHash());
        decision.setDecisionHash(HashChainUtil.chainHash(decision.getPrevHash(), hashPayload(decision)));
        decisionMapper.insert(decision);

        if (decision.getRiskLevel() == AgentDecision.RISK_LOW && !dryRun) {
            execute(decision);
        }
        log.info("[AgentDecision] submit id={} agent={} action={} risk={} status={} hash={}",
                decision.getId(), decision.getAgentId(), decision.getActionType(),
                decision.getRiskLevel(), decision.getStatus(), decision.getDecisionHash());
        return decision;
    }

    /**
     * HITL 审批回写（06.3）：驳回任意阶段直接终结；
     * 中风险单人批准即执行；高风险需两名不同审批人先后批准（双人复核）后才执行。
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentDecision approve(Long id, boolean pass, String reason, Long approverId) {
        if (approverId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        AgentDecision decision = decisionMapper.selectById(id);
        if (decision == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "决策单不存在");
        }
        if (decision.getStatus() != AgentDecision.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "决策单已终结，不允许重复审批");
        }
        if (decision.getRiskLevel() == AgentDecision.RISK_LOW) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "低风险决策自动执行，无需人工审批");
        }

        if (!pass) {
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "驳回必须填写原因");
            }
            if (decision.getFirstApprover() == null) {
                decision.setFirstApprover(approverId);
                decision.setFirstApproveTime(LocalDateTime.now());
            } else {
                decision.setSecondApprover(approverId);
                decision.setSecondApproveTime(LocalDateTime.now());
            }
            decision.setStatus(AgentDecision.STATUS_REJECTED);
            decision.setRejectReason(reason);
            decisionMapper.updateById(decision);
            return decision;
        }

        if (decision.getFirstApprover() == null) {
            decision.setFirstApprover(approverId);
            decision.setFirstApproveTime(LocalDateTime.now());
            if (decision.getRiskLevel() == AgentDecision.RISK_HIGH) {
                // 高风险一审通过：保持待审批，等待第二名复核人
                decisionMapper.updateById(decision);
                return decision;
            }
        } else {
            if (decision.getRiskLevel() != AgentDecision.RISK_HIGH) {
                throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "该决策单审批流已完成");
            }
            if (Objects.equals(decision.getFirstApprover(), approverId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "高风险双人复核要求二审与一审为不同审批人");
            }
            decision.setSecondApprover(approverId);
            decision.setSecondApproveTime(LocalDateTime.now());
        }

        decision.setStatus(AgentDecision.STATUS_APPROVED);
        decisionMapper.updateById(decision);
        execute(decision);
        return decision;
    }

    /** 审批通过/低风险放行后执行：成功置 3已执行，失败置 4执行失败（决策单留痕不回滚） */
    private void execute(AgentDecision decision) {
        try {
            String result = decisionExecutor.execute(decision);
            decision.setStatus(AgentDecision.STATUS_EXECUTED);
            decision.setExecuteResult(result);
        } catch (Exception e) {
            log.warn("[AgentDecision] execute failed id={} action={}", decision.getId(), decision.getActionType(), e);
            decision.setStatus(AgentDecision.STATUS_EXECUTE_FAILED);
            decision.setExecuteResult("EXECUTE_FAILED:" + e.getMessage());
        }
        decision.setExecuteTime(LocalDateTime.now());
        decisionMapper.updateById(decision);
    }

    /** 决策流分页（驾驶舱）：待审批优先、风险高在前、新单在前 */
    public PageVO<AgentDecision> page(Integer status, Integer riskLevel, String agentId,
                                      Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<AgentDecision> wrapper = new LambdaQueryWrapper<AgentDecision>()
                .eq(status != null, AgentDecision::getStatus, status)
                .eq(riskLevel != null, AgentDecision::getRiskLevel, riskLevel)
                .eq(agentId != null && !agentId.isBlank(), AgentDecision::getAgentId, agentId)
                .orderByAsc(AgentDecision::getStatus)
                .orderByDesc(AgentDecision::getRiskLevel)
                .orderByDesc(AgentDecision::getId);
        return PageVO.of(decisionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    public AgentDecision detail(Long id) {
        AgentDecision decision = decisionMapper.selectById(id);
        if (decision == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "决策单不存在");
        }
        return decision;
    }

    /** 驾驶舱聚合：各 Agent 吞吐 + 待审批风险分布 + 哈希链健康校验 */
    public AgentCockpitVO cockpit() {
        List<AgentDecision> all = decisionMapper.selectList(new LambdaQueryWrapper<AgentDecision>()
                .orderByAsc(AgentDecision::getId));
        AgentCockpitVO vo = new AgentCockpitVO();

        Map<String, AgentCockpitVO.AgentStat> stats = new LinkedHashMap<>();
        Map<Integer, Long> pendingByRisk = new TreeMap<>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        for (AgentDecision d : all) {
            AgentCockpitVO.AgentStat stat = stats.computeIfAbsent(d.getAgentId(), k -> {
                AgentCockpitVO.AgentStat s = new AgentCockpitVO.AgentStat();
                s.setAgentId(k);
                s.setTotal(0L);
                s.setPending(0L);
                s.setExecuted(0L);
                s.setFailed(0L);
                s.setTodayCount(0L);
                return s;
            });
            stat.setTotal(stat.getTotal() + 1);
            if (d.getStatus() == AgentDecision.STATUS_PENDING) {
                stat.setPending(stat.getPending() + 1);
                pendingByRisk.merge(d.getRiskLevel(), 1L, Long::sum);
            } else if (d.getStatus() == AgentDecision.STATUS_EXECUTED) {
                stat.setExecuted(stat.getExecuted() + 1);
            } else if (d.getStatus() == AgentDecision.STATUS_EXECUTE_FAILED) {
                stat.setFailed(stat.getFailed() + 1);
            }
            if (d.getCreateTime() != null && !d.getCreateTime().isBefore(todayStart)) {
                stat.setTodayCount(stat.getTodayCount() + 1);
            }
        }
        vo.setAgents(new ArrayList<>(stats.values()));
        vo.setPendingByRisk(pendingByRisk);
        vo.setChainLength((long) all.size());
        vo.setChainValid(verifyChain(all));
        vo.setLastDecisionHash(all.isEmpty() ? null : all.get(all.size() - 1).getDecisionHash());
        return vo;
    }

    /** 全链校验：逐条重算 chainHash 并核对 prev 链接 */
    private boolean verifyChain(List<AgentDecision> ascending) {
        String prev = HashChainUtil.GENESIS_HASH;
        for (AgentDecision d : ascending) {
            if (!prev.equals(d.getPrevHash())
                    || !HashChainUtil.verify(d.getPrevHash(), hashPayload(d), d.getDecisionHash())) {
                return false;
            }
            prev = d.getDecisionHash();
        }
        return true;
    }

    /** 入链载荷：决策不可变要素（审批/执行回写不参与哈希，保证链校验稳定） */
    private static Map<String, Object> hashPayload(AgentDecision d) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", d.getAgentId());
        payload.put("actionType", d.getActionType());
        payload.put("targetUri", d.getTargetUri());
        payload.put("paramsJson", d.getParamsJson());
        payload.put("evidencesJson", d.getEvidencesJson());
        payload.put("riskLevel", d.getRiskLevel());
        return payload;
    }
}

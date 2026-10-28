package com.dianfei.business.agent.controller;

import com.dianfei.business.agent.dto.DecisionApproveDTO;
import com.dianfei.business.agent.entity.AgentDecision;
import com.dianfei.business.agent.service.AgentDecisionService;
import com.dianfei.business.agent.vo.AgentCockpitVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · Agent 驾驶舱（C5 / 06.3，对接 Track E F8）：
 * 决策流展示、待审批队列、HITL 审批回写；审批契约 POST /api/agent/decision/{id}/approve（12.2 冻结）。
 * 审批动作本身 @OperLog 留痕，形成「AI 决策 → 人工审批 → 执行回写 → 入链」闭环。
 */
@Tag(name = "后台-Agent驾驶舱")
@RestController
@RequestMapping("/api")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentDecisionService agentDecisionService;

    @Operation(summary = "决策流分页（待审批优先、风险高在前；status/riskLevel/agentId 过滤）")
    @GetMapping("/admin/agent/decisions")
    public Result<PageVO<AgentDecision>> decisions(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) Integer riskLevel,
                                                   @RequestParam(required = false) String agentId) {
        return Result.success(agentDecisionService.page(status, riskLevel, agentId, pageNum, pageSize));
    }

    @Operation(summary = "决策单详情（含依据链与执行回写）")
    @GetMapping("/admin/agent/decisions/{id}")
    public Result<AgentDecision> detail(@PathVariable Long id) {
        return Result.success(agentDecisionService.detail(id));
    }

    @Operation(summary = "驾驶舱聚合：各 Agent 吞吐 / 待审批风险分布 / 哈希链健康")
    @GetMapping("/admin/agent/cockpit")
    public Result<AgentCockpitVO> cockpit() {
        return Result.success(agentDecisionService.cockpit());
    }

    @Operation(summary = "HITL 审批（12.2 冻结契约）：中风险单人批准即执行，高风险双人复核；驳回需原因")
    @OperLog(value = "Agent 决策单审批", module = "Agent驾驶舱")
    @PostMapping("/agent/decision/{id}/approve")
    public Result<AgentDecision> approve(@PathVariable Long id, @Valid @RequestBody DecisionApproveDTO dto) {
        return Result.success(agentDecisionService.approve(id, dto.getPass(), dto.getReason(), UserContext.getUserId()));
    }
}

package com.dianfei.business.agent.controller;

import com.dianfei.business.agent.dto.DecisionSubmitDTO;
import com.dianfei.business.agent.entity.AgentDecision;
import com.dianfei.business.agent.service.AgentDecisionService;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 系统账号 · 决策单提交（C5 / F8）：
 * principal_type=AGENT 白名单主体调用（D17 身份三分）；低风险自动执行，中/高风险进 HITL 审批队列
 */
@Tag(name = "Agent-决策单提交")
@RestController
@RequestMapping("/api/agent")
@RequiresRole("AGENT")
@RequiredArgsConstructor
public class AgentDecisionController {

    private final AgentDecisionService agentDecisionService;

    @Operation(summary = "提交决策单（入链哈希；1低自动执行 / 2中运营审批 / 3高双人复核）")
    @PostMapping("/decisions")
    public Result<AgentDecision> submit(@Valid @RequestBody DecisionSubmitDTO dto) {
        return Result.success(agentDecisionService.submit(dto));
    }
}

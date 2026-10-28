package com.dianfei.ai.agent.controller;

import com.dianfei.ai.agent.dto.AgentRunDTO;
import com.dianfei.ai.agent.service.AgentRunService;
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

import java.util.Map;

/**
 * F8 多智能体运营中枢 · AI 运行入口（12 章 /api/agent，Track E 唯一职责）：
 * 触发 Agent 运行（perceive→plan→act/guard→audit）并把决策草稿交 business 模块
 * {@code AgentDecisionService.submitBatch} 落库（哈希入链 + 风险路由 + HITL）。
 * 决策分页/审批/驾驶舱等查询均由 business 模块 AdminAgentController 提供（/admin/agent/**）。
 */
@Tag(name = "Agent-运营中枢（F8）")
@RestController
@RequestMapping("/api/agent")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRunService agentRunService;

    @Operation(summary = "触发一次 Agent 运行（perceive→plan→act/guard→audit），决策单落库")
    @PostMapping("/run")
    public Result<Map<String, Object>> run(@Valid @RequestBody AgentRunDTO dto) {
        return Result.success(agentRunService.run(dto));
    }
}

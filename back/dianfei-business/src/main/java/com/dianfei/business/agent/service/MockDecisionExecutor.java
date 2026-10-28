package com.dianfei.business.agent.service;

import com.dianfei.business.agent.entity.AgentDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 决策执行器（C5 占位）：business 模块独立测试（H2）使用的兜底实现，仅记录日志并返回执行摘要。
 * 全量应用中真实实现为 dianfei-ai 模块的 Whitelist 决策执行器（@Primary 实现本接口契约，
 * 白名单 target_uri 代 Agent 调用业务服务，对齐 06.2 行动可控 / D17）。
 */
@Slf4j
@Component
public class MockDecisionExecutor implements DecisionExecutor {

    @Override
    public String execute(AgentDecision decision) {
        log.info("[MockDecisionExecutor] 模拟执行决策 id={} action={} target={}",
                decision.getId(), decision.getActionType(), decision.getTargetUri());
        return "MOCK_EXECUTED:" + decision.getActionType();
    }
}

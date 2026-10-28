package com.dianfei.business.agent.service;

import com.dianfei.business.agent.entity.AgentDecision;

/**
 * 决策单执行器契约（C5 冻结）：审批通过（或低风险自动放行）后代为调用 target_uri 白名单接口。
 * Track E 决策执行框架就绪后替换为真实 REST 回调实现（Agent 只能走平台既有接口，对齐 06.2）。
 */
public interface DecisionExecutor {

    /**
     * 执行决策动作
     *
     * @return 执行结果描述（回写 execute_result）
     * @throws RuntimeException 执行失败时抛出，由调用方置为 4执行失败
     */
    String execute(AgentDecision decision);
}

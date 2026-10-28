package com.dianfei.business.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 驾驶舱聚合视图（C5 / 06.3）：
 * 各 Agent 实时吞吐 + 待审批队列分布 + 审计链（哈希链）健康状态
 */
@Data
@Schema(description = "Agent 驾驶舱聚合视图")
public class AgentCockpitVO {

    @Schema(description = "各 Agent 运行统计")
    private List<AgentStat> agents;

    @Schema(description = "待审批数按风险等级分布 key=riskLevel(1/2/3)")
    private Map<Integer, Long> pendingByRisk;

    @Schema(description = "决策哈希链校验是否通过（全量入链，AI 行为不可抵赖）")
    private Boolean chainValid;

    @Schema(description = "链上决策总数")
    private Long chainLength;

    @Schema(description = "链尾（最新一条）决策哈希")
    private String lastDecisionHash;

    @Data
    @Schema(description = "单 Agent 运行统计")
    public static class AgentStat {

        @Schema(description = "Agent 标识")
        private String agentId;

        @Schema(description = "决策总数")
        private Long total;

        @Schema(description = "待审批")
        private Long pending;

        @Schema(description = "已执行")
        private Long executed;

        @Schema(description = "执行失败")
        private Long failed;

        @Schema(description = "今日决策数（吞吐）")
        private Long todayCount;
    }
}

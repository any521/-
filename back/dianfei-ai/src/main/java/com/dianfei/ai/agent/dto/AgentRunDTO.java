package com.dianfei.ai.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * F8 Agent 运行入参（后台触发 / 定时巡检共用）
 */
@Data
public class AgentRunDTO {

    @Schema(description = "Agent 类型：AGENT-01 调度 / AGENT-02 风控 / AGENT-03 客服 / AGENT-04 复盘")
    @NotBlank
    @Pattern(regexp = "AGENT-0[1-4]", message = "agentType 须为 AGENT-01~04")
    private String agentType;

    @Schema(description = "待处理事件列表")
    @NotEmpty
    @Valid
    private List<Event> events;

    @Schema(description = "true=只产出决策单不执行（低风险也不自动执行）")
    private Boolean dryRun;

    @Data
    public static class Event {

        @Schema(description = "事件类型：PENDING_ORDER / RISK_ALERT / USER_TICKET / DAILY_METRICS")
        @NotBlank
        private String eventType;

        @Schema(description = "事件载荷（进决策单 params_json）")
        private Map<String, Object> payload;
    }
}

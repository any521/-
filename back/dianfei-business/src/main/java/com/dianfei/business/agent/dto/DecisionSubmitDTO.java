package com.dianfei.business.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Agent 决策单提交入参（C5 / Track E F8 冻结契约）：
 * 「动作 + 参数 + 依据链 + 风险等级」；AI 运行产出的草稿在 AgentRunService 内转换为本 DTO 后统一落库
 */
@Data
@Schema(description = "Agent 决策单提交入参")
public class DecisionSubmitDTO {

    @NotBlank
    @Schema(description = "发起 Agent 标识，如 dispatch-agent / risk-agent")
    private String agentId;

    @NotBlank
    @Schema(description = "动作类型：DISPATCH_ADJUST/PRICE_CHANGE/POINTS_ADJUST 等")
    private String actionType;

    @Schema(description = "目标接口 URI（审批通过后由框架代为调用）")
    private String targetUri;

    @Schema(description = "动作参数快照 JSON")
    private String paramsJson;

    @Schema(description = "依据链 JSON（source/description/dataRef 数组，供审批人查看）")
    private String evidencesJson;

    @NotNull
    @Min(1)
    @Max(3)
    @Schema(description = "风险等级：1低(自动执行) 2中(运营审批) 3高(双人复核)")
    private Integer riskLevel;
}

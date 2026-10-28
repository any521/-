package com.dianfei.business.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Agent 决策单审批入参（C5 HITL）：pass=false 时 reason 必填（服务层校验）
 */
@Data
@Schema(description = "Agent 决策单审批入参")
public class DecisionApproveDTO {

    @NotNull
    @Schema(description = "true 批准 / false 驳回")
    private Boolean pass;

    @Schema(description = "驳回原因（pass=false 必填）")
    private String reason;
}

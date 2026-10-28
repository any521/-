package com.dianfei.ai.config.dto;

import com.dianfei.ai.config.entity.AiRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 能力路由保存请求（管理端）
 */
@Data
@Schema(description = "AI 能力路由保存请求")
public class AiRouteDTO {

    @Schema(description = "编辑时传，新增不传")
    private Long id;

    @NotBlank
    @Schema(description = "能力标识：visual_verify/repair_diagnose/...")
    private String capability;

    @Schema(description = "路由链")
    private List<AiRoute.ChainHop> chain;

    @NotBlank
    @Schema(description = "降级代号：MANUAL_REVIEW/KNOWLEDGE_RULE/...")
    private String fallback;

    @Schema(description = "重试次数，默认 1")
    private Integer maxRetries;

    @Schema(description = "重试温度衰减，默认 0.5")
    private BigDecimal retryTemperatureDecay;

    @Schema(description = "0禁用 1启用")
    private Integer enabled;

    private String remark;
}

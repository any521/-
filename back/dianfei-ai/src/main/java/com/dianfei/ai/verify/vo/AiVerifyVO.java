package com.dianfei.ai.verify.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * F1 审核结果（编排层最终决策，已落 biz_ai_verify_log）
 */
@Data
@Schema(description = "F1 图片视觉审核结果")
public class AiVerifyVO {

    @Schema(description = "审核日志 ID（biz_ai_verify_log）")
    private String logId;

    @Schema(description = "最终决策：AUTO_PASS 自动通过 / MANUAL_REVIEW 积分冻结待人审 / REJECT 驳回")
    private String decision;

    @Schema(description = "识别品类")
    private String category;

    @Schema(description = "申报品类是否一致")
    private Boolean categoryMatch;

    @Schema(description = "纯净度 0-1")
    private Double purity;

    @Schema(description = "置信度 0-1")
    private Double confidence;

    @Schema(description = "违规项")
    private List<String> violations;

    @Schema(description = "驳回/转人审原因")
    private String rejectReason;

    @Schema(description = "是否走了降级链（备选模型/mock/AI 宕机转人审）")
    private Boolean degraded;

    @Schema(description = "端到端耗时 ms（P95 ≤ 5000）")
    private Integer elapsedMs;
}

package com.dianfei.ai.verify.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * CAM 云端精分类结果（/api/ai/classify，契约 C6-2 字段冻结，与 ai-back ClassifyDetailResponse 逐字段对齐）
 */
@Data
@Schema(description = "CAM 云端精分类结果")
public class ClassifyVO {

    @Schema(description = "具体物品名（≤20 字），识别失败为空串")
    private String itemName;

    @Schema(description = "品类（8 类中文枚举之一，契约 C6-6）")
    private String category;

    @Schema(description = "置信度 0-1")
    private Double confidence;

    @Schema(description = "纯净度 0-1")
    private Double purity;

    @Schema(description = "投放建议")
    private String advice;

    @Schema(description = "当日报价 元/kg（厨余/未知为 null，前端用 RECYCLE_CATEGORIES 兜底）")
    private Double pricePerKg;

    @Schema(description = "是否走了降级链（备选模型/mock/AI 宕机）")
    private Boolean degraded;

    @Schema(description = "AI 侧耗时 ms")
    private Integer elapsedMs;

    @Schema(description = "provider/model，降级时为空")
    private String modelName;
}

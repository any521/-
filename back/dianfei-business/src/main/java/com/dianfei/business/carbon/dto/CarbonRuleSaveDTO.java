package com.dianfei.business.carbon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 碳积分规则配置（PUT /api/admin/carbon-rule）：按 orderType+category 幂等 upsert
 */
@Data
@Schema(description = "碳积分规则配置")
public class CarbonRuleSaveDTO {

    @NotBlank(message = "规则编码不能为空")
    @Schema(description = "规则编码，如 RECYCLE_METAL")
    private String ruleCode;

    @NotBlank(message = "业务类型不能为空")
    @Schema(description = "RECYCLE/KITCHEN/REPAIR")
    private String orderType;

    @NotBlank(message = "品类不能为空")
    @Schema(description = "品类（字典驱动），厨余用 DEFAULT")
    private String category;

    @Schema(description = "每公斤积分（回收/维修）")
    private BigDecimal pointsPerKg;

    @Schema(description = "每袋/件积分（厨余/维修按件）")
    private BigDecimal pointsPerBag;

    @Schema(description = "每公斤减排 kgCO2")
    private BigDecimal carbonPerKg;

    @Schema(description = "每袋/件减排 kgCO2")
    private BigDecimal carbonPerBag;

    @Schema(description = "0停用 1启用，默认 1")
    private Integer status;
}

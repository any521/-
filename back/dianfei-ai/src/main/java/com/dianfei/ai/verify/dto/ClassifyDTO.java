package com.dianfei.ai.verify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * CAM 云端精分类请求（/api/ai/classify，契约 C6-2）：
 * 入参用驼峰 imageUrl 与前端对齐（契约类 AiContracts 统一 snake_case，仅用于 Java→Python 透传）。
 */
@Data
@Schema(description = "CAM 云端精分类请求（裁剪目标图 base64）")
public class ClassifyDTO {

    @NotBlank
    @Schema(description = "裁剪目标区域 base64（≤300KB，契约 C6-3）")
    private String imageUrl;

    @Schema(description = "端侧大类 code 先验（paper/plastic/...，可空）")
    private String categoryHint;
}

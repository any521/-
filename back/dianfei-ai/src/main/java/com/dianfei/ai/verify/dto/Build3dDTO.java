package com.dianfei.ai.verify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 3D 模型构建请求（/api/ai/verify/3d-build）：
 * 入参用驼峰 imageUrl 与前端对齐（契约类 AiContracts 统一 snake_case，仅用于 Java→Python 透传）。
 */
@Data
@Schema(description = "3D 模型构建请求（img2threejs 风格）")
public class Build3dDTO {

    @NotBlank
    @Schema(description = "图片地址（data URL 或内网 HTTP）")
    private String imageUrl;

    /** 构建质量模式：standard（默认）/ advanced / extreme；非法值按 standard 处理 */
    @Schema(description = "构建质量模式：standard/advanced/extreme（默认 standard）")
    private String mode;
}

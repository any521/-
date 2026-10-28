package com.dianfei.ai.verify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F1 审核请求（12 章 /api/ai/verify/image）：
 * exifTime 由上传端解析透传（MinIO 直传后前端/上传服务读 EXIF），缺失或过旧强制人审
 */
@Data
@Schema(description = "F1 图片视觉审核请求")
public class AiVerifyDTO {

    @NotBlank
    @Schema(description = "业务来源：RECYCLE 回收完成照 / KITCHEN 垃圾投放照")
    private String bizType;

    @Schema(description = "来源单 ID（回收单/投放上报）；下单前品类识别场景为空")
    private Long bizId;

    @NotBlank
    @Schema(description = "图片 URL（MinIO 内网可达）")
    private String imageUrl;

    @Schema(description = "申报品类（识别品类不一致时禁止自动通过）")
    private String claimedCategory;

    @Schema(description = "申报袋数（垃圾投放）")
    private Integer claimedBags;

    @Schema(description = "EXIF 拍摄时间（上传端解析透传；缺失或超时强制人审）")
    private LocalDateTime exifTime;
}

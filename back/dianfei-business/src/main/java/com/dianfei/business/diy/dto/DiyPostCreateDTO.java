package com.dianfei.business.diy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布变废为宝作品入参（LoopFill B6）：标题/内容必填，图片选填
 */
@Data
@Schema(description = "发布 DIY 作品参数")
public class DiyPostCreateDTO {

    @Schema(description = "作品标题", example = "牛奶盒变身小花盆")
    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长 64 字")
    private String title;

    @Schema(description = "改造心得/教程", example = "清洗晾干后剪开，种上绿萝…")
    @NotBlank(message = "内容不能为空")
    @Size(max = 500, message = "内容最长 500 字")
    private String content;

    @Schema(description = "作品图（base64 压缩 ≤300KB 或 MinIO URL，选填）")
    private String imageUrl;
}

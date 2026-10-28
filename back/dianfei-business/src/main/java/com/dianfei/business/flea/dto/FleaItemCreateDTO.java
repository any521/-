package com.dianfei.business.flea.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布闲置（Upgrade U3）：title/description 必填限长，price ≥1，category 枚举在 Service 校验；
 * imageUrl 可选，须以 /api/file/preview/ 或 http(s) 开头（防任意 URL 注入，同头像上传约束）。
 */
@Data
public class FleaItemCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题最长 64 字")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 500, message = "描述最长 500 字")
    private String description;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotNull(message = "积分定价不能为空")
    @Min(value = 1, message = "积分定价至少 1")
    private Long price;

    /** 图片地址，可选 */
    @Size(max = 255, message = "图片地址最长 255")
    private String imageUrl;
}

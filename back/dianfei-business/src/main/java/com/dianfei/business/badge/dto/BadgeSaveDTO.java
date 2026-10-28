package com.dianfei.business.badge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 徽章保存入参（管理端）：id 为空新增，非空编辑（含上下架状态）；
 * iconUrl 可选，须以 /api/file/preview/ 或 http(s) 开头（同头像上传约束）。
 */
@Data
@Schema(description = "徽章保存入参")
public class BadgeSaveDTO {

    @Schema(description = "徽章 ID（空=新增）")
    private Long id;

    @NotBlank(message = "徽章编码不能为空")
    @Size(max = 32, message = "徽章编码最长 32 字")
    private String code;

    @NotBlank(message = "徽章名称不能为空")
    @Size(max = 32, message = "徽章名称最长 32 字")
    private String name;

    @NotBlank(message = "徽章描述不能为空")
    @Size(max = 128, message = "徽章描述最长 128 字")
    private String description;

    @NotNull(message = "兑换积分不能为空")
    @Min(value = 1, message = "兑换积分至少 1")
    private Long price;

    /** 徽章图标/封面地址，可选 */
    @Size(max = 255, message = "图标地址最长 255")
    private String iconUrl;

    private Integer sort;

    /** 0下架 1上架 */
    @NotNull(message = "上架状态不能为空")
    private Integer status;
}

package com.dianfei.business.science.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论 DTO（V50）
 */
@Data
public class CommentDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容最多 500 字")
    private String content;
}

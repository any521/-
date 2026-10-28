package com.dianfei.business.science.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章评分 DTO（V51）
 */
@Data
public class RatingDTO {

    /** 评分：1-5 星 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer score;

    /** 短评（可选） */
    @Size(max = 500, message = "短评最多 500 字")
    private String comment;
}

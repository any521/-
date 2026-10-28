package com.dianfei.business.science.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章评分表（V42）
 */
@Data
@TableName("biz_science_rating")
public class ScienceRating {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long articleId;
    private Long userId;
    private Integer score;  // 1-5 星
    private String comment;
}

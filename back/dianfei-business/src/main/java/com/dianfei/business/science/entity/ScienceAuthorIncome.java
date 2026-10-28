package com.dianfei.business.science.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 作者积分收益统计表（V42）
 */
@Data
@TableName("biz_science_author_income")
public class ScienceAuthorIncome {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long articleId;
    private Long authorId;
    private Integer totalDownloads;
    private Integer totalPoints;
    private String lastIncomeTime;
}

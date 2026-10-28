package com.dianfei.business.science.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章点赞记录表（V42）
 */
@Data
@TableName("biz_science_like")
public class ScienceLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long articleId;
    private Long userId;
}

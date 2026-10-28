package com.dianfei.business.science.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科普文章评论表（V50）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_science_comment")
public class ScienceComment extends BaseEntity {

    private Long articleId;
    private Long userId;
    private String content;
    /** 1=展示，0=屏蔽 */
    private Integer status;
}

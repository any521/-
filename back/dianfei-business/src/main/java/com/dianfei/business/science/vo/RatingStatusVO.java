package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 当前用户评分状态 VO（V51）
 */
@Data
@Builder
public class RatingStatusVO {

    /** 当前用户是否已评分 */
    private Boolean rated;

    /** 已评分数（未评分时为 0） */
    private Integer score;
}

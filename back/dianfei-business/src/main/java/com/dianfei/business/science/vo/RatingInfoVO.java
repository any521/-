package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 评分信息 VO（V42）
 */
@Data
@Builder
public class RatingInfoVO {
    private Double average;
    private Integer count;
}

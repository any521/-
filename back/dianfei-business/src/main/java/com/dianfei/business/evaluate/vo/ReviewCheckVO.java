package com.dianfei.business.evaluate.vo;

import lombok.Data;

/**
 * 本单是否已评价（GET /api/reviews/check，订单详情页回显用）
 */
@Data
public class ReviewCheckVO {

    /** 是否已评价 */
    private Boolean reviewed;

    /** 已评价时的星级（未评价为 null） */
    private Integer rating;
}

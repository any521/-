package com.dianfei.business.evaluate.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的评价分页行（GET /api/reviews/mine，连业务订单号展示）
 */
@Data
public class ReviewVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** RECYCLE / KITCHEN / REPAIR */
    private String orderType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 业务订单号（垃圾投放无单号，展示 KR+id） */
    private String orderNo;

    /** 星级 1-5 */
    private Integer rating;

    private String comment;

    private LocalDateTime createTime;
}

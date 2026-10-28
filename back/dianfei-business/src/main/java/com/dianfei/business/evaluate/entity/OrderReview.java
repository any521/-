package com.dianfei.business.evaluate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单评价（biz_order_review，GapFill B2）：
 * 用户对已完成订单按「订单类型+订单+本人」唯一评价（uk_review），rating 1-5；
 * staff_id 冗余订单接单员工，供员工绩效好评率聚合（StaffPerformanceMapper.selectPraiseRate）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order_review")
public class OrderReview extends BaseEntity {

    /** RECYCLE / KITCHEN / REPAIR */
    private String orderType;

    /** 业务订单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 评价用户 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 服务员工（订单接单员工；垃圾投放无员工为空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    /** 星级 1-5 */
    private Integer rating;

    /** 评价内容（可选） */
    private String comment;
}

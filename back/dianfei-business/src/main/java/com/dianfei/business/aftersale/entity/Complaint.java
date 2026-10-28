package com.dianfei.business.aftersale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单售后单（biz_complaint，C4）：投诉/退款/追责/补发积分/纠纷仲裁
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_complaint")
public class Complaint extends BaseEntity {

    /** 待处理 */
    public static final int STATUS_PENDING = 0;
    /** 已处理 */
    public static final int STATUS_HANDLED = 1;
    /** 已驳回 */
    public static final int STATUS_REJECTED = 2;

    public static final int TYPE_COMPLAINT = 1;
    public static final int TYPE_REFUND = 2;
    public static final int TYPE_ACCOUNTABILITY = 3;
    public static final int TYPE_REISSUE_POINTS = 4;
    public static final int TYPE_ARBITRATION = 5;

    /** 来源单类型：RECYCLE/KITCHEN/REPAIR/MALL（OrderTypeEnum） */
    private String orderType;

    /** 来源单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 1投诉 2退款 3追责 4补发积分 5纠纷仲裁 */
    private Integer type;

    private String content;

    /** 0待处理 1已处理 2已驳回 */
    private Integer status;

    /** 处理结论（退款金额/补发积分数/仲裁结果） */
    private String handleResult;

    /** 处理人（后台管理员 userId） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handlerId;

    private LocalDateTime handleTime;
}

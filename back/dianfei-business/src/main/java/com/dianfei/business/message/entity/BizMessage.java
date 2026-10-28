package com.dianfei.business.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内信（B5，biz_message）：关键节点触发落库——
 * 派单成功 DISPATCHED / 订单完成 ORDER_FINISHED / 积分到账 POINTS_ARRIVED / 积分冻结 POINTS_FROZEN。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_message")
public class BizMessage extends BaseEntity {

    /** 未读 */
    public static final int UNREAD = 0;
    /** 已读 */
    public static final int READ = 1;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 消息类型（MessageSender.TYPE_*） */
    private String msgType;

    private String title;

    private String content;

    /** 0未读 1已读 */
    private Integer readFlag;
}

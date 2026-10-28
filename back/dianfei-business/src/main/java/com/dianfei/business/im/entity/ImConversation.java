package com.dianfei.business.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IM 会话（onsite03，biz_im_conversation）：
 * 按「bizType + bizId」幂等创建/复用；成员 = 下单用户 + 接单员工（biz_staff.user_id）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_im_conversation")
public class ImConversation extends BaseEntity {

    /** RECYCLE/REPAIR（后续可扩展 KITCHEN 等） */
    private String bizType;

    /** 关联订单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    /** 下单用户 sys_user.id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 接单员工 sys_user.id（biz_staff.user_id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffUserId;

    /** 最后一条消息类型（列表预览） */
    private String lastMsgType;

    /** 最后一条消息摘要（文本截断/文件类型名） */
    private String lastMsg;

    private LocalDateTime lastTime;

    /** 用户侧未读 */
    private Integer unreadUser;

    /** 员工侧未读 */
    private Integer unreadStaff;
}

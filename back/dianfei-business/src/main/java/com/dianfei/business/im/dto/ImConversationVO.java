package com.dianfei.business.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 会话出参 VO（列表 + 未读）：对方昵称/头像由 sys_user 拼装
 */
@Data
@Schema(description = "IM 会话出参")
public class ImConversationVO {

    private Long id;

    /** RECYCLE/REPAIR */
    private String bizType;

    /** 关联订单 ID */
    private Long bizId;

    /** 对方昵称（sys_user.nickname 兜底 username） */
    private String peerName;

    /** 对方头像（sys_user.avatar） */
    private String peerAvatar;

    /** 最后一条消息摘要（列表预览） */
    private String lastMsg;

    /** 最后一条消息时间 */
    private LocalDateTime lastTime;

    /** 本人侧未读数 */
    private Integer unread;
}

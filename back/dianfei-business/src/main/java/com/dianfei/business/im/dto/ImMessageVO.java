package com.dianfei.business.im.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * IM 消息出参 VO（历史分页 + 下行 MSG 帧载体）：Long 统一转 String
 */
@Data
@Schema(description = "IM 消息出参")
public class ImMessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long convId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    /** USER/STAFF */
    private String senderRole;

    /** 发送者昵称（history/下行帧填充，前端气泡展示） */
    private String senderName;

    /** 发送者头像（/api/file/preview/avatar/**，空则前端占位） */
    private String senderAvatar;

    /** TEXT/IMAGE/STICKER/VOICE/VIDEO/SYSTEM */
    private String msgType;

    /** 文本或文件 URL（/api/file/preview/**） */
    private String content;

    /** VOICE/VIDEO 时长秒 */
    private Integer duration;

    private LocalDateTime createTime;

    public static ImMessageVO of(Long id, Long convId, Long senderId, String senderRole,
                                 String msgType, String content, Integer duration, LocalDateTime createTime) {
        ImMessageVO vo = new ImMessageVO();
        vo.setId(id);
        vo.setConvId(convId);
        vo.setSenderId(senderId);
        vo.setSenderRole(senderRole);
        vo.setMsgType(msgType);
        vo.setContent(content);
        vo.setDuration(duration == null ? 0 : duration);
        vo.setCreateTime(createTime);
        return vo;
    }

    /** 下行 MSG 帧的 msg 载荷（保持契约 msg 三字段 + 附加 id/createTime 供前端幂等渲染） */
    public Map<String, Object> toFrameMsg() {
        return Map.of(
                "msgType", msgType,
                "content", content == null ? "" : content,
                "duration", duration == null ? 0 : duration,
                "id", String.valueOf(id),
                "createTime", createTime == null ? "" : createTime.toString().replace('T', ' ').substring(0, 16));
    }
}

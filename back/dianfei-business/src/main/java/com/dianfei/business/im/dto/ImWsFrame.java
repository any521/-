package com.dianfei.business.im.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Map;

/**
 * IM WebSocket 帧（C-O3-3 冻结协议，上下行同构）：
 * 上行 { type: MSG|READ|PING|TYPING, convId, msg: { msgType, content, duration } }
 * 下行 { type: MSG|READ|SYSTEM|PONG|LOCATION, convId, msg, senderId, senderRole, location }
 * 附加字段（不破坏冻结契约，前端幂等渲染用）：messageId / createTime
 */
@Data
public class ImWsFrame {

    /** MSG|READ|PING|PONG|TYPING|SYSTEM|LOCATION */
    private String type;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long convId;

    /** MSG 消息载荷（msgType/content/duration 冻结） */
    private ImSendDTO msg;

    /** 下行：发送者 sys_user.id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    /** 下行：USER/STAFF */
    private String senderRole;

    /** 下行 LOCATION：{ lng, lat }（onsite04 消费） */
    private Map<String, Object> location;

    /** 附加：下行 MSG 的消息 ID（幂等渲染） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    /** 附加：下行 MSG 的发送时间 yyyy-MM-dd HH:mm */
    private String createTime;

    /** 附加：READ 帧已读者（对端据此标记「已读」） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long readerId;

    /** 附加：下行 MSG 发送者昵称（气泡展示） */
    private String senderName;

    /** 附加：下行 MSG 发送者头像（气泡展示） */
    private String senderAvatar;

    public static ImWsFrame of(String type, Long convId) {
        ImWsFrame frame = new ImWsFrame();
        frame.setType(type);
        frame.setConvId(convId);
        return frame;
    }
}

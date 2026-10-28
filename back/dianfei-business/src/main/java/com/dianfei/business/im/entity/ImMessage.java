package com.dianfei.business.im.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IM 消息（onsite03，biz_im_message）：
 * 表无 deleted/审计列（对齐 V23 DDL），故不继承 BaseEntity，仅 id + createTime。
 */
@Data
@TableName("biz_im_message")
public class ImMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long convId;

    /** 发送者 sys_user.id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    /** USER/STAFF（区分会话两侧展示） */
    private String senderRole;

    /** TEXT/IMAGE/STICKER/VOICE/VIDEO/SYSTEM */
    private String msgType;

    /** 文本或文件 URL（/api/file/preview/**） */
    private String content;

    /** VOICE/VIDEO 时长秒 */
    private Integer duration;

    /** 1正常 2撤回（预留） */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

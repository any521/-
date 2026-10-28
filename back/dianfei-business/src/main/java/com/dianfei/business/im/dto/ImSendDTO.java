package com.dianfei.business.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 消息发送内容（C-O3-3 帧协议 msg 字段：msgType/content/duration 冻结） */
@Data
@Schema(description = "IM 消息内容")
public class ImSendDTO {

    @Schema(description = "TEXT/IMAGE/STICKER/VOICE/VIDEO/SYSTEM")
    @NotBlank(message = "消息类型不能为空")
    private String msgType;

    @Schema(description = "文本或文件 URL")
    private String content;

    @Schema(description = "VOICE/VIDEO 时长秒")
    private Integer duration;
}

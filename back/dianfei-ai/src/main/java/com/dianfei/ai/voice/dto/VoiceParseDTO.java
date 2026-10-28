package com.dianfei.ai.voice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** F7 语音解析请求（对齐 ai-back schemas.voice.VoiceParseRequest，userId 取登录态） */
@Data
public class VoiceParseDTO {

    /** 会话 ID（首轮为空由后端生成，多轮传回同一 ID 补槽） */
    private String sessionId;

    /** ASR 转写文本（或降级后的用户直接输入） */
    @NotBlank(message = "语音文本不能为空")
    @Size(max = 500, message = "文本过长")
    private String text;

    /** V44 图片扫描升级：可选图片 base64（data URL 或纯 base64，≤5MB），携带时管家先做图片识别 */
    @Size(max = 7 * 1024 * 1024, message = "图片过大")
    private String imageBase64;

    /** 照片持久化：本轮上传照片 URL（/api/file/preview/voice/...，前端先上传文件服务），落库留痕 */
    @Size(max = 500, message = "图片地址过长")
    private String imageUrl;
}

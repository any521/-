package com.dianfei.ai.voice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * F7 语音碳管家会话轨迹（biz_voice_session，init_trackE.sql 冻结）：
 * 一次语音交互多轮共享 session_id，每轮一行（用户文本 + 解析结果 + 回复），成单后回填 order_id
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_voice_session")
public class VoiceSession extends BaseEntity {

    /** 会话 ID（一次语音交互多轮共享） */
    private String sessionId;

    private Long userId;

    /** 轮次 */
    private Integer turnNo;

    /** ASR 转写/用户输入文本 */
    private String userText;

    /** 识别意图：BOOK_RECYCLING/QUERY_ORDER/…/UNKNOWN */
    private String intent;

    /** 槽位快照 {category, appoint_time, address} */
    private String slotsJson;

    /** TTS 播报/回复文本 */
    private String replyText;

    private Double confidence;

    /** 1=解析降级（回退文本输入） */
    private Integer degraded;

    /** 成单后回填回收单 ID */
    private Long orderId;

    /** 本轮用户上传照片 URL（/api/file/preview/voice/...，未传图/上传失败为 null） */
    private String imageUrl;
}

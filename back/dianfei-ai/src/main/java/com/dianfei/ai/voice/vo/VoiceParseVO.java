package com.dianfei.ai.voice.vo;

import com.dianfei.ai.client.dto.AiContracts;
import lombok.Data;

import java.util.List;

/** F7 语音解析出参：意图/槽位/回复 + 成单结果（orderId 非空即已自动预约） */
@Data
public class VoiceParseVO {

    private String sessionId;

    private Integer turnNo;

    /** BOOK_RECYCLING / QUERY_ORDER / QUERY_CARBON_ACCOUNT / CANCEL_ORDER / SMALL_TALK / UNKNOWN */
    private String intent;

    private AiContracts.VoiceSlots slots;

    private Double confidence;

    /** TTS 播报/文字回复 */
    private String replyText;

    /** 仍缺失的必填槽位（驱动前端多轮追问） */
    private List<String> needSlots;

    /** 槽位齐全自动成单后的回收单 ID（未成单为 null） */
    private String orderId;

    /** true=解析降级，前端回退文本输入表单 */
    private Boolean degraded;

    /** V44 图片扫描升级：识别后图片（标注检测框的 JPEG base64），未携带图片为 null */
    private String annotatedImageBase64;

    /** V44 图片扫描升级：检测明细（空=未携带图片/无目标） */
    private List<AiContracts.YoloDetectionItem> detections;
}

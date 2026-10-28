package com.dianfei.ai.verify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * F1 视觉审核日志（biz_ai_verify_log，init_trackE.sql 冻结）：
 * 三层管线落库快照——pHash 防重 + EXIF 校验（Java）→ 模型识别（AI 服务）→ 阈值决策
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_ai_verify_log")
public class AiVerifyLog extends BaseEntity {

    /** 业务来源：RECYCLE 回收完成照 / KITCHEN 垃圾投放照 */
    private String bizType;

    /** 来源单 ID（回收单/投放上报） */
    private Long bizId;

    /** 提交用户 */
    private Long userId;

    private String imageUrl;

    /** 感知哈希（64bit hex，防重复图） */
    private String phash;

    /** EXIF 拍摄时间（缺失为 NULL，过旧强制人审） */
    private LocalDateTime exifTime;

    /** 实际命中模型 provider/model；人审兜底为 NULL */
    private String modelName;

    /** AI 结构化出参快照 */
    private String resultJson;

    /** 识别品类 */
    private String category;

    /** 识别置信度 0-1 */
    private Double confidence;

    /** 纯净度 0-1 */
    private Double purity;

    /** AUTO_PASS 自动通过 / MANUAL_REVIEW 积分冻结待人审 / REJECT 驳回 */
    private String decision;

    /** 驳回/转人审原因（重复图/违规项/低置信/AI 不可用） */
    private String rejectReason;

    /** 1=走了降级链（备选模型/mock/AI 宕机转人审） */
    private Integer degraded;

    /** 端到端耗时（P95 ≤ 5000 监控口径） */
    private Integer elapsedMs;

    /** 请求追踪 ID（贯穿 Java/Python 日志） */
    private String traceId;
}

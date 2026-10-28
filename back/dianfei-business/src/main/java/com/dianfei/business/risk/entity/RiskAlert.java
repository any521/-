package com.dianfei.business.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 风控告警记录（biz_risk_alert，B4 产出 / C4 后台复核）：
 * 冻结发放的积分经人工复核后解冻返还或驳回扣除
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_risk_alert")
public class RiskAlert extends BaseEntity {

    /** 待复核 */
    public static final int STATUS_PENDING = 0;
    /** 通过（解冻发放） */
    public static final int STATUS_PASSED = 1;
    /** 驳回（违规扣除） */
    public static final int STATUS_REJECTED = 2;

    /** 告警业务类型：KITCHEN_REPORT 等 */
    private String bizType;

    /** 来源业务 ID */
    private Long bizId;

    private Long userId;

    private Long areaId;

    /** 触发原因（偏离倍数等） */
    private String reason;

    /** 冻结积分数 */
    private Long frozenPoints;

    /** 冻结流水 ID（复核回写账本用） */
    private Long freezeLogId;

    /** 0待复核 1通过(解冻) 2驳回(扣减) */
    private Integer status;
}

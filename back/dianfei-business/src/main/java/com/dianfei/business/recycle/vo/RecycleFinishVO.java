package com.dianfei.business.recycle.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 完成登记结果 VO（B3）：核算金额 + 碳积分到账信息（哈希链流水 ID 可溯源）
 */
@Data
@Builder
public class RecycleFinishVO {

    private String orderId;
    private String orderNo;
    /** 实收总重量 kg */
    private BigDecimal totalWeight;
    /** 核算回款金额（元） */
    private BigDecimal amount;
    /** 本单积分 */
    private long points;
    /** 到账后余额 */
    private long balanceAfter;
    /** 工作人员同步获得的回收积分（与用户相同，bizKey 含 userId 同单两笔不冲突） */
    private long staffPoints;
    /** 工作人员到账后余额 */
    private long staffBalanceAfter;
    /** 本单减排量 kgCO2 */
    private BigDecimal carbonReduced;
    /** 碳流水 ID（可用 /api/carbon/verify/{logId} 验证） */
    private String carbonLogId;
    /** 服务费结算状态（onsite02：0未结算 1已结算 2异常转人工；无服务费为 0/空） */
    private Integer settleStatus;
    /** 服务费积分（onsite02：用户承诺值，0=未使用） */
    private Long settlePoints;
    /** 服务费结算结果文案（onsite02：前端展示用，无服务费为 null） */
    private String settleResult;
}

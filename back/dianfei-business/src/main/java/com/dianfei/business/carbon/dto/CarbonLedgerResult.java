package com.dianfei.business.carbon.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账本写入结果：流水 ID 供业务单回填溯源（如 biz_mall_exchange.carbon_log_id）
 */
@Data
@Builder
public class CarbonLedgerResult {

    /** 流水 ID（biz_carbon_log.id） */
    private Long logId;
    /** 本笔积分变动（正入负出） */
    private long points;
    /** 变动后余额 */
    private long balanceAfter;
    /** 本笔减排量 kgCO2 */
    private BigDecimal carbonReduced;
    /** 命中的规则编码 */
    private String ruleCode;
    /** 是否为幂等命中（同 bizKey 已存在，未重复入账） */
    private boolean duplicated;
}

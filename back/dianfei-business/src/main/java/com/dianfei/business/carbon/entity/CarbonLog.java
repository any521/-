package com.dianfei.business.carbon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 碳积分流水（B2，哈希链，生产环境仅 INSERT）：
 * curr_hash = SHA256(prev_hash + canonicalJson(payload))，按账户逐条链接
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_carbon_log")
public class CarbonLog extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 来源单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** RECYCLE/KITCHEN/REPAIR/MALL */
    private String orderType;

    /** 1获得 2消费 3补发 4扣减 5清零 6冻结 7解冻（CarbonLogTypeEnum） */
    private Integer type;

    /** 正=入账 负=出账 */
    private Long points;

    private Long balanceAfter;

    /** 命中的规则编码 */
    private String ruleCode;

    /** 本笔减排量 kgCO2 */
    private BigDecimal carbonReduced;

    /** 幂等键：order_type:order_id:type */
    private String bizKey;

    /** 备注（调整原因等） */
    private String remark;

    /** 上一条哈希（同账户），首条为 GENESIS */
    private String prevHash;

    /** SHA-256(prev_hash + payload) */
    private String currHash;

    /** 关联每日锚定（锚定任务回填） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long anchorId;
}

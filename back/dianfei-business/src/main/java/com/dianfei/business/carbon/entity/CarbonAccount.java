package com.dianfei.business.carbon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 碳积分账户（B2）：可用积分 + 风控冻结积分 + 累计减排量
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_carbon_account")
public class CarbonAccount extends BaseEntity {

    private Long userId;

    /** 可用积分 */
    private Long pointsBalance;

    /** 冻结积分（风控） */
    private Long frozenPoints;

    /** 累计减排量 kgCO2 */
    private BigDecimal totalCarbonReduced;
}

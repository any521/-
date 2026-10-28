package com.dianfei.business.carbon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 碳积分规则（B2，配置化）：order_type+category 唯一，新增字典品类+规则零代码发分
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_carbon_rule")
public class CarbonRule extends BaseEntity {

    /** 厨余等无品类业务使用的默认品类 */
    public static final String CATEGORY_DEFAULT = "DEFAULT";

    /** 规则编码，如 RECYCLE_METAL */
    private String ruleCode;

    /** RECYCLE/KITCHEN/REPAIR */
    private String orderType;

    /** 品类（字典驱动），厨余用 DEFAULT */
    private String category;

    /** 每公斤积分（回收/维修） */
    private BigDecimal pointsPerKg;

    /** 每袋积分（厨余） */
    private BigDecimal pointsPerBag;

    /** 每公斤减排 kgCO2 */
    private BigDecimal carbonPerKg;

    /** 每袋减排 kgCO2 */
    private BigDecimal carbonPerBag;

    /** 0停用 1启用 */
    private Integer status;
}

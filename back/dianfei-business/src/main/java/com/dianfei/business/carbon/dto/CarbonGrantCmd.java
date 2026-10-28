package com.dianfei.business.carbon.dto;

import com.dianfei.common.enums.OrderTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发分命令：orderType+category 命中 biz_carbon_rule，weight（kg）或 bagCount/quantity 二选一
 */
@Data
@Builder
public class CarbonGrantCmd {

    private Long userId;
    private OrderTypeEnum orderType;
    /** 来源单 ID，与 orderType 组成幂等键 */
    private Long orderId;
    /** 品类（字典驱动）；维修替代换新用 REPAIR_REPLACE */
    private String category;
    /** 重量 kg（回收/维修按件时可空） */
    private BigDecimal weight;
    /** 袋数/件数（厨余按袋、维修按件） */
    private Integer quantity;
}

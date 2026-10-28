package com.dianfei.business.carbon.dto;

import com.dianfei.common.enums.OrderTypeEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 消费扣分命令（商城兑换）：points 为正数，实现方以负值记流水
 */
@Data
@Builder
public class CarbonConsumeCmd {

    private Long userId;
    private OrderTypeEnum orderType;
    /** 来源单 ID（兑换单），与 orderType 组成幂等键 */
    private Long orderId;
    /** 扣减积分（正数） */
    private long points;
    private String remark;
}

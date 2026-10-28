package com.dianfei.business.carbon.dto;

import com.dianfei.common.enums.OrderTypeEnum;
import lombok.Builder;
import lombok.Data;

/**
 * 后台调整命令：points 正=补发 / 负=违规扣减（@OperLog 留痕由调用方保证）
 */
@Data
@Builder
public class CarbonAdjustCmd {

    private Long userId;
    private OrderTypeEnum orderType;
    /** 关联单 ID（售后单/告警单），与 orderType+类型组成幂等键 */
    private Long orderId;
    /** 正=补发 负=扣减 */
    private long points;
    /** 调整原因（留痕） */
    private String reason;
}

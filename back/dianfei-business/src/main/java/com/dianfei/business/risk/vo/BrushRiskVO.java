package com.dianfei.business.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 恶意刷单嫌疑用户（C4）：时间窗内高频兑换聚合结果，供后台冻结/清零处置
 */
@Data
@Schema(description = "刷单嫌疑用户")
public class BrushRiskVO {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "时间窗内有效兑换单数")
    private Long orderCount;

    @Schema(description = "时间窗内累计消耗积分")
    private Long totalPoints;
}

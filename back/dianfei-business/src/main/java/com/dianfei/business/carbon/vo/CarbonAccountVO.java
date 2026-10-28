package com.dianfei.business.carbon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 碳积分账户视图（GET /api/carbon/account）
 */
@Data
@Builder
@Schema(description = "碳积分账户")
public class CarbonAccountVO {

    @Schema(description = "可用积分")
    private long pointsBalance;

    @Schema(description = "冻结积分（风控）")
    private long frozenPoints;

    @Schema(description = "累计减排量 kgCO2")
    private BigDecimal totalCarbonReduced;

    @Schema(description = "本月减排量 kgCO2（type=1 获得类，口径同首页摘要）")
    private BigDecimal monthCarbon;

    @Schema(description = "碳减排全站排名百分位 0-100（超过 X% 的用户，无人高于自己=100）")
    private Integer rankPercent;
}

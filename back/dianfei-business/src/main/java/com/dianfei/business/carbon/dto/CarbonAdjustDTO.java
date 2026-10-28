package com.dianfei.business.carbon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台积分调整请求（补发 points>0 / 扣减 points<0）
 */
@Data
@Schema(description = "后台积分调整请求")
public class CarbonAdjustDTO {

    @NotNull(message = "userId 不能为空")
    @Schema(description = "目标用户")
    private Long userId;

    @Schema(description = "关联业务类型 RECYCLE/KITCHEN/REPAIR/MALL（与 orderId 组成幂等键，可空）")
    private String orderType;

    @Schema(description = "关联单 ID（售后单/告警单，可空）")
    private Long orderId;

    @NotNull(message = "points 不能为空")
    @Schema(description = "正=补发 负=扣减")
    private Long points;

    @Schema(description = "调整原因（留痕）")
    private String reason;
}

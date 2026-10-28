package com.dianfei.ai.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动联网更新同步入参（地区选择：城市可空，空=IP 定位默认）
 */
@Data
public class MarketSyncDTO {

    @Schema(description = "同步城市（如 北京/上海；空=IP 定位默认）")
    @Size(max = 32, message = "城市最长 32 字")
    private String city;
}

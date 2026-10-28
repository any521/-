package com.dianfei.business.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户端首页平台环保数据（复用数据大屏 C5 聚合口径，登录用户可读）
 */
@Schema(description = "首页平台环保数据")
@Data
public class HomeStatsVO {

    @Schema(description = "累计回收订单数")
    private Long recycleOrders;

    @Schema(description = "累计碳减排 kgCO2（可信账本口径）")
    private BigDecimal carbonReduced;

    @Schema(description = "注册用户数")
    private Long totalUsers;

    @Schema(description = "维修服务次数")
    private Long repairOrders;
}

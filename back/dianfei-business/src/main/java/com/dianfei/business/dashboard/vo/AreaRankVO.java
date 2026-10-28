package com.dianfei.business.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 片区排行行（C5 大屏 / 驾驶舱地图）：回收量 + 垃圾投放量 + 碳减排按片区聚合，按回收重量降序
 */
@Data
@Schema(description = "片区排行")
public class AreaRankVO {

    @Schema(description = "片区 ID")
    private Long areaId;

    @Schema(description = "片区名称")
    private String areaName;

    @Schema(description = "片区中心经度（驾驶舱地图标注）")
    private BigDecimal lng;

    @Schema(description = "片区中心纬度")
    private BigDecimal lat;

    @Schema(description = "完成回收单数")
    private Long recycleOrders;

    @Schema(description = "回收总重量 kg")
    private BigDecimal recycleWeight;

    @Schema(description = "垃圾投放总袋数")
    private Long kitchenBags;

    @Schema(description = "碳减排量 kgCO2（RECYCLE 账本流水口径，流水为空时服务层按碳系数折算）")
    private BigDecimal carbonReduced;
}

package com.dianfei.business.diy.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 片区聚合中间结果（LoopFill B6 排行榜 Mapper 直查）：areaId + 指标值
 * （回收 kg / 垃圾投放袋数 / 碳减排 kg 三查询共用；垃圾投放袋数 CAST AS SIGNED 后仍以 BigDecimal 承载）
 */
@Data
public class DiyAreaAggVO {

    private Long areaId;

    private BigDecimal value;
}

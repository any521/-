package com.dianfei.business.kitchen.vo;

import lombok.Data;

/**
 * 片区袋数聚合（B4）：聚合定时任务与片区看板共用
 */
@Data
public class AreaBagAggVO {

    private Long areaId;

    private Long totalBags;

    private Long reportCount;
}

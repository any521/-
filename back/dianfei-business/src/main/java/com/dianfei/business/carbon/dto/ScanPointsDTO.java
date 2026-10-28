package com.dianfei.business.carbon.dto;

import lombok.Data;

/**
 * 扫描识别积分发放入参（CAM cam04，契约 C6-9）：category/itemName 仅用于流水留痕，
 * 均为可空（缺失不阻断发放，防刷由 Redis 日频控承担）。
 */
@Data
public class ScanPointsDTO {

    /** 品类中文名（8 类枚举之一，入 remark） */
    private String category;

    /** 具体物品名（≤20 字，入 remark） */
    private String itemName;
}

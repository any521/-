package com.dianfei.business.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分商城商品（biz_mall_product，C3）：库存条件更新防超卖
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mall_product")
public class MallProduct extends BaseEntity {

    /** 上架 */
    public static final int STATUS_ON = 1;
    /** 下架 */
    public static final int STATUS_OFF = 0;

    private String productName;

    /** 商品图（MinIO） */
    private String coverUrl;

    /** 所需积分 */
    private Long pointsPrice;

    /** 库存（条件更新防超卖） */
    private Integer stock;

    /** 0下架 1上架 */
    private Integer status;

    private String description;
}

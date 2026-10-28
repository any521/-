package com.dianfei.business.flea.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 跳蚤市场闲置商品（Upgrade U3，biz_flea_item）：C2C 自由交易商品。
 * 状态机：0下架 1在售 2已售出 3违规下架；售出/回售由订单域条件更新驱动（防双卖）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_flea_item")
public class FleaItem extends BaseEntity {

    /** 下架 */
    public static final int STATUS_OFF = 0;
    /** 在售 */
    public static final int STATUS_ON_SALE = 1;
    /** 已售出 */
    public static final int STATUS_SOLD = 2;
    /** 违规下架 */
    public static final int STATUS_FORBIDDEN = 3;

    /** 卖家 */
    private Long userId;

    private String title;

    private String description;

    /** REUSABLE/TOY/BOOK/ELECTRONIC/HOME/OTHER */
    private String category;

    /** 积分定价 ≥1 */
    private Long price;

    /** 图片（/api/file/upload?biz=flea） */
    private String imageUrl;

    /** 0下架 1在售 2已售出 3违规下架 */
    private Integer status;
}

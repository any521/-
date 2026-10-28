package com.dianfei.business.badge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成就徽章（biz_badge，积分市场合并升级）：积分兑换虚拟收藏品，
 * 内置 10 枚种子（id 9501-9510），管理员可维护；兑换走账本 consume（order_type=BADGE）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_badge")
public class BizBadge extends BaseEntity {

    /** 上架 */
    public static final int STATUS_ON = 1;
    /** 下架 */
    public static final int STATUS_OFF = 0;

    /** 徽章编码，稳定标识 */
    private String code;

    private String name;

    private String description;

    /** 兑换所需积分 ≥1 */
    private Long price;

    /** 徽章图标/封面（/api/file/upload?biz=badge） */
    private String iconUrl;

    private Integer sort;

    /** 0下架 1上架 */
    private Integer status;
}

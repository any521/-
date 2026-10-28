package com.dianfei.business.badge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 徽章兑换记录（biz_badge_exchange，积分市场合并升级）：
 * 虚拟商品兑换即到账，无物流状态机；carbon_log_id 关联扣分流水溯源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_badge_exchange")
public class BizBadgeExchange extends BaseEntity {

    /** 已兑换（唯一有效态，虚拟商品即到账） */
    public static final int STATUS_EXCHANGED = 0;

    /** 雪花号，前缀 BE */
    private String exchangeNo;

    private Long userId;

    private Long badgeId;

    /** 徽章名快照 */
    private String badgeName;

    /** 徽章图标快照 */
    private String badgeIcon;

    /** 本单扣减积分 */
    private Long pointsCost;

    /** 关联扣分流水（biz_carbon_log.id，溯源） */
    private Long carbonLogId;

    /** 0已兑换 */
    private Integer status;
}

package com.dianfei.business.flea.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 跳蚤订单（Upgrade U3，biz_flea_order）：积分托管结算全链路——
 * 下单冻结（freeze）→ 发货 → 收货结算（transfer）→ 评价；取消退冻（unfreeze）。
 * 状态机：0待发货 → 1待收货 → 2已完成；0待发货 → 3已取消（全部节点幂等）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_flea_order")
public class FleaOrder extends BaseEntity {

    /** 待发货（已冻结，可取消） */
    public static final int STATUS_PENDING_SHIP = 0;
    /** 待收货（已发货） */
    public static final int STATUS_SHIPPED = 1;
    /** 已完成（已结算） */
    public static final int STATUS_FINISHED = 2;
    /** 已取消（已退冻） */
    public static final int STATUS_CANCELLED = 3;

    /** 雪花号，前缀 FO */
    private String orderNo;

    /** 买家 */
    private Long userId;

    /** 卖家 */
    private Long sellerId;

    private Long itemId;

    /** 商品名快照 */
    private String itemTitle;

    /** 商品图快照 */
    private String itemImage;

    /** 成交积分快照 */
    private Long price;

    /** 买家收货地址 */
    private String address;

    /** 0待发货 1待收货 2已完成 3已取消 */
    private Integer status;

    /** 评价 1-5（完成后可评一次） */
    private Integer rating;

    /** 评价内容 */
    private String comment;

    /** 下单冻结流水（取消解冻凭据） */
    private Long freezeLogId;

    private LocalDateTime shipTime;

    private LocalDateTime receiveTime;

    private LocalDateTime cancelTime;
}

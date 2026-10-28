package com.dianfei.business.flea.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跳蚤订单 VO（Upgrade U3）：买卖双方视角共用，回填双方昵称/头像 + 状态描述 + 时间字段；
 * 下单响应额外带 frozenPoints（冻结积分数）。
 */
@Data
@Builder
public class FleaOrderVO {

    private Long id;

    private String orderNo;

    private Long itemId;

    private String itemTitle;

    private String itemImage;

    private Long price;

    private String address;

    /** 0待发货 1待收货 2已完成 3已取消 */
    private Integer status;

    private String statusDesc;

    private Integer rating;

    private String comment;

    private Long buyerId;

    private String buyerNickname;

    private String buyerAvatar;

    private Long sellerId;

    private String sellerNickname;

    private String sellerAvatar;

    /** 下单冻结流水（溯源，取消解冻凭据） */
    private Long freezeLogId;

    /** 下单响应：冻结积分数 */
    private Long frozenPoints;

    private LocalDateTime createTime;

    private LocalDateTime shipTime;

    private LocalDateTime receiveTime;

    private LocalDateTime cancelTime;
}

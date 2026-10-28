package com.dianfei.business.message;

/**
 * 站内信发送契约（B5）：B 线关键节点触发，真实通道由 Track A 通知骨架实现后替换默认实现
 */
public interface MessageSender {

    /** 消息类型：派单成功 */
    String TYPE_DISPATCHED = "DISPATCHED";
    /** 消息类型：订单创建成功（用户预约回收/维修后即时回执） */
    String TYPE_ORDER_CREATED = "ORDER_CREATED";
    /** 消息类型：订单完成 */
    String TYPE_ORDER_FINISHED = "ORDER_FINISHED";
    /** 消息类型：积分到账 */
    String TYPE_POINTS_ARRIVED = "POINTS_ARRIVED";
    /** 消息类型：积分冻结 */
    String TYPE_POINTS_FROZEN = "POINTS_FROZEN";
    /** 消息类型：投放上报待审核（B4 垃圾投放人工审核） */
    String TYPE_KITCHEN_PENDING = "KITCHEN_PENDING";
    /** 消息类型：投放审核驳回（B4） */
    String TYPE_AUDIT_REJECTED = "AUDIT_REJECTED";

    // ---- 交易消息 6 类（U5：跳蚤五节点 + 违规下架；U3 flea 包统一引用本常量） ----
    /** 消息类型：跳蚤新订单（卖家：有新订单待发货） */
    String TYPE_FLEA_NEW_ORDER = "FLEA_NEW_ORDER";
    /** 消息类型：跳蚤已发货（买家：卖家已发货） */
    String TYPE_FLEA_SHIPPED = "FLEA_SHIPPED";
    /** 消息类型：跳蚤已完成（卖家：买家确认收货，积分到账） */
    String TYPE_FLEA_FINISHED = "FLEA_FINISHED";
    /** 消息类型：跳蚤已取消（买家：冻结积分退回） */
    String TYPE_FLEA_CANCELLED = "FLEA_CANCELLED";
    /** 消息类型：跳蚤评价（卖家/买家：收到对方评价） */
    String TYPE_FLEA_REVIEWED = "FLEA_REVIEWED";
    /** 消息类型：商品违规下架（卖家提醒） */
    String TYPE_ITEM_OFF_SHELF = "ITEM_OFF_SHELF";

    /** 消息类型：管理员发布的通知（noticeType=2；公告不群发，仅全局展示） */
    String TYPE_NOTICE = "NOTICE";

    // ---- 消息分类分组（前端消息页 tab 用，避免前端维护散列表） ----
    /** 消息分组：订单（ORDER_CREATED/DISPATCHED/ORDER_FINISHED/FLEA_*） */
    String GROUP_ORDER = "ORDER";
    /** 消息分组：积分（POINTS_ARRIVED/POINTS_FROZEN） */
    String GROUP_POINTS = "POINTS";
    /** V42: 消息分组：科普文章 (ARTICLE_PUBLISHED/ARTICLE_DOWNLOADED) */
    String GROUP_ARTICLE = "ARTICLE";

    void send(Long userId, String type, String title, String content);
}

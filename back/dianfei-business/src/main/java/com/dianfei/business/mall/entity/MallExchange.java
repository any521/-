package com.dianfei.business.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分兑换订单（biz_mall_exchange，C3）：carbon_log_id 关联扣分流水溯源。
 * 状态机：0已兑换 → 1已发货 → 2已完成；0 可取消 → 3已取消（积分退回+库存返还）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mall_exchange")
public class MallExchange extends BaseEntity {

    /** 已兑换（待发货，可取消） */
    public static final int STATUS_EXCHANGED = 0;
    /** 已发货 */
    public static final int STATUS_SHIPPED = 1;
    /** 已完成 */
    public static final int STATUS_COMPLETED = 2;
    /** 已取消（积分退回） */
    public static final int STATUS_CANCELLED = 3;

    /** 雪花号，前缀 MO */
    private String exchangeNo;

    private Long userId;

    private Long productId;

    /** 商品名快照 */
    private String productName;

    /** 本单扣减积分总额 */
    private Long pointsCost;

    private Integer quantity;

    /** 关联扣分流水（biz_carbon_log.id，溯源） */
    private Long carbonLogId;

    private Integer status;

    /** 收货地址（实物） */
    private String address;
}

package com.dianfei.business.recycle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可回收物上门回收订单（B3，对齐 10.2 冻结 DDL）：
 * 状态机 0待接单 → 1已接单 → 2进行中 → 3完成 / 4取消 / 5异常（OrderStatusEnum），非法流转抛 2003。
 * repair_ref 承载维修转回收联动溯源（11.2），transfer_to 承载转运去向溯源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recycle_order")
public class RecycleOrder extends BaseEntity {

    /** 雪花号，前缀 RO */
    private String orderNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 指派/接单回收员（biz_staff.id），派单前为空 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    /** 订单所属片区（员工端按 biz_staff.area_id 过滤） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    /** 品类（字典驱动） */
    private String category;

    /** 用户预估重量 kg */
    private BigDecimal estWeight;

    /** 实收重量 kg（完成登记） */
    private BigDecimal weight;

    /** 件数 */
    private Integer quantity;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    /** 预约上门时间 */
    private LocalDateTime appointTime;

    /** 0待接单 1已接单 2进行中 3完成 4取消 5异常（OrderStatusEnum） */
    private Integer status;

    /** 核算回款金额（完成登记按报价核算） */
    private BigDecimal amount;

    /** 转运去向（溯源） */
    private String transferTo;

    /** 联动来源维修订单 ID（与 biz_repair_order.recycle_ref 双向） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long repairRef;

    private String cancelReason;

    /** 异常上报原因 */
    private String exceptionReason;

    /** 用户承诺服务费积分（onsite02：0=不使用积分支付，完成时转账给回收员） */
    private Long servicePoints;

    /** 服务费结算状态（onsite02：0未结算 1已结算 2免结算/异常转人工） */
    private Integer settleStatus;

    private LocalDateTime finishTime;
}

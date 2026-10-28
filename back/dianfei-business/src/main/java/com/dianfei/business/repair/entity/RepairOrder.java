package com.dianfei.business.repair.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电路维修工单（biz_repair_order，11.2）。
 * 状态见 RepairStatusEnum：0待接单 1已接单 2检修中 3已完成 4已取消 5已转回收
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_repair_order")
public class RepairOrder extends BaseEntity {

    /** 雪花号，前缀 RP */
    private String orderNo;
    private Long userId;
    /** 指派维修技术员（biz_staff.id） */
    private Long staffId;
    /** 订单所属片区（员工端按 biz_staff.area_id 过滤） */
    private Long areaId;
    /** 故障类型（字典驱动） */
    private String faultType;
    /** 故障描述（自由备注，AI 降级入口） */
    private String faultDesc;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    /** 上门时段-起 */
    private LocalDateTime appointStart;
    /** 上门时段-止 */
    private LocalDateTime appointEnd;
    /** 以旧换新意向 0否 1是 */
    private Integer oldForNew;
    /** 天然气配送顺带 0否 1是 */
    private Integer gasDelivery;
    /** F2 预诊结果 JSON（AI 降级时为空） */
    private String diagnoseJson;
    /** 可修复性判定 0未判定 1可修复 2不可修复 */
    private Integer repairable;
    /** 检修报告 JSON */
    private String reportJson;
    /** 科普留痕 */
    private String scienceNote;
    /** 联动生成的回收订单 ID（与 biz_recycle_order.repair_ref 双向） */
    private Long recycleRef;
    private Integer status;
    private String cancelReason;

    /** 用户承诺服务费积分（onsite02：0=不使用积分支付，完成时转账给技术员） */
    private Long servicePoints;

    /** 服务费结算状态（onsite02：0未结算 1已结算 2免结算/异常转人工） */
    private Integer settleStatus;

    private LocalDateTime finishTime;
}

package com.dianfei.business.repair.linkage.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回收工单（biz_recycle_order，Track B 冻结 DDL 的联动侧最小映射）：
 * 仅承载 C2 转回收写入与双单溯源查询所需字段；B 就绪后以 B 的完整实体为准。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recycle_order")
public class LinkedRecycleOrder extends BaseEntity {

    /** 待接单（对齐 B 的 OrderStatusEnum） */
    public static final int STATUS_PENDING = 0;

    /** 雪花号，前缀 RO */
    private String orderNo;
    private Long userId;
    /** 指派回收员（联动生成时为空，待 B 派单） */
    private Long staffId;
    private Long areaId;
    /** 品类（字典驱动，联动件取维修故障类型） */
    private String category;
    private Integer quantity;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    /** 预约上门时间 */
    private LocalDateTime appointTime;
    private Integer status;
    /** 联动来源维修订单 ID（与 biz_repair_order.recycle_ref 双向） */
    private Long repairRef;
}

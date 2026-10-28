package com.dianfei.business.repair.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 维修配件登记（biz_repair_part）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_repair_part")
public class RepairPart extends BaseEntity {

    /** 关联维修工单 */
    private Long orderId;
    private String partName;
    private String partModel;
    private Integer quantity;
    /** 配件单价（元） */
    private BigDecimal price;
}

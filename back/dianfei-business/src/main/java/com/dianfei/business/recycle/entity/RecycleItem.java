package com.dianfei.business.recycle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 回收物资明细（B3）：完成登记时按报价逐条核算金额，供台账与后台详情
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recycle_item")
public class RecycleItem extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    private String category;

    /** 实收重量 kg */
    private BigDecimal weight;

    /** 按报价核算金额（weight * price_per_kg） */
    private BigDecimal amount;
}

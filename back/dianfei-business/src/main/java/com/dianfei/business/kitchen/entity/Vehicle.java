package com.dianfei.business.kitchen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 清运车辆（B4，biz_vehicle）：聚合任务按容量（袋）给出派车建议
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_vehicle")
public class Vehicle extends BaseEntity {

    /** 车牌号（唯一） */
    private String vehicleNo;

    /** 常驻片区（可空=机动车辆） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    /** 容量（袋） */
    private Integer capacity;

    /** 0停用 1可用 */
    private Integer status;
}

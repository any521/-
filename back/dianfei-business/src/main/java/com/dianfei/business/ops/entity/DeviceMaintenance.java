package com.dianfei.business.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车辆设备运维登记（biz_device_maintenance，C4）：保养/维修/年检记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_device_maintenance")
public class DeviceMaintenance extends BaseEntity {

    /** 清运车辆 */
    public static final int TARGET_VEHICLE = 1;
    /** 设备 */
    public static final int TARGET_DEVICE = 2;

    /** 1清运车辆 2设备 */
    private Integer targetType;

    /** 车牌号/设备编号 */
    private String targetNo;

    /** 运维内容（保养/维修/年检） */
    private String content;

    /** 费用（元） */
    private BigDecimal cost;

    private LocalDate maintDate;

    /** 登记人 */
    private Long operatorId;
}

package com.dianfei.business.kitchen.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 厨余清运任务（B4，biz_kitchen_task）：
 * 每晚 23 点后聚合当日上报袋数，达片区 clean_threshold 生成次日任务与派车建议；
 * 环卫员次日 5-6 点执行并归档（总重量/车号/终点/凭证），归档时对当日上报统一发分。
 * uk_task_area_date(area_id, task_date) 保证聚合任务幂等可重跑。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_kitchen_task")
public class KitchenTask extends BaseEntity {

    /** 待执行 */
    public static final int STATUS_PENDING = 0;
    /** 已完成（归档） */
    public static final int STATUS_FINISHED = 1;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    /** 清运日期（上报次日） */
    private LocalDate taskDate;

    /** 片区名称（只读展示字段：员工端列表真实投放片区，联表 biz_area 填充） */
    @TableField(exist = false)
    private String areaName;

    /** 聚合袋数 */
    private Integer totalBags;

    /** 派车建议（biz_vehicle） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long vehicleId;

    /** 执行环卫员（biz_staff） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    /** 0待执行 1已完成 */
    private Integer status;

    /** 归档总重量 kg */
    private BigDecimal totalWeight;

    /** 实际车号 */
    private String vehicleNo;

    /** 终点（溯源台账） */
    private String destination;

    /** 凭证照片（MinIO） */
    private String proofUrl;

    private LocalDateTime finishTime;
}

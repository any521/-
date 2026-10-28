package com.dianfei.dispatch.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VRPTW 访问点（F4）：一张待派回收单 = 一个上门停靠点。
 * 时间窗为预约时间 ± 容忍分钟（sys_config dispatch.window-tolerance-min），
 * 均以"计划日 00:00 起分钟数"表达，避免时区/日期换算进求解内核。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitStop {

    @PlanningId
    private Long orderId;

    private String orderNo;

    private String address;

    /** 经纬度（缺失坐标的订单不进求解，由服务层垫后处理） */
    private double lng;

    private double lat;

    /** 预估重量 kg（容量约束口径，实收未知） */
    private double demandKg;

    /** 时间窗开（分钟，自计划日 00:00） */
    private long windowStartMin;

    /** 时间窗关（分钟）：迟于此到达记硬约束违反 */
    private long windowEndMin;

    /** 单点服务时长（分钟） */
    private long serviceMin;
}


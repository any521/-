package com.dianfei.dispatch.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * VRPTW 车辆/回收员路线（F4 规划实体）：Timefold 对 visits 列表变量做构造启发 + 局部搜索，
 * 求解产出即该员工当日按序上门路线。出发点取片区订单质心（MVP 无车场坐标，见 memory.md 开放问题）。
 */
@Data
@NoArgsConstructor
@PlanningEntity
public class VehicleRoute {

    @PlanningId
    private Long staffId;

    private Long staffUserId;

    private String staffName;

    /** 单员工日承载上限 kg（sys_config dispatch.capacity-kg） */
    private double capacityKg;

    /** 出发坐标（片区质心） */
    private double startLng;

    private double startLat;

    /** 班次开始（分钟，自计划日 00:00） */
    private long shiftStartMin;

    @PlanningListVariable
    private List<VisitStop> visits = new ArrayList<>();

    public VehicleRoute(Long staffId, Long staffUserId, String staffName, double capacityKg,
                        double startLng, double startLat, long shiftStartMin) {
        this.staffId = staffId;
        this.staffUserId = staffUserId;
        this.staffName = staffName;
        this.capacityKg = capacityKg;
        this.startLng = startLng;
        this.startLat = startLat;
        this.shiftStartMin = shiftStartMin;
    }
}


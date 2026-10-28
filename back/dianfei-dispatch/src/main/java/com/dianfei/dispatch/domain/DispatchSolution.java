package com.dianfei.dispatch.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * VRPTW 求解方案（F4）：同片区在岗回收员 × 当日待派订单。
 * 硬约束=容量超载/时间窗迟到，软约束=总行驶距离（DispatchScoreCalculator）。
 */
@Data
@NoArgsConstructor
@PlanningSolution
public class DispatchSolution {

    @PlanningEntityCollectionProperty
    private List<VehicleRoute> vehicles;

    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<VisitStop> visits;

    @PlanningScore
    private HardSoftLongScore score;

    public DispatchSolution(List<VehicleRoute> vehicles, List<VisitStop> visits) {
        this.vehicles = vehicles;
        this.visits = visits;
    }
}

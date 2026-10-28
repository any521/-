package com.dianfei.dispatch.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.calculator.EasyScoreCalculator;
import com.dianfei.dispatch.domain.DispatchSolution;
import com.dianfei.dispatch.domain.VehicleRoute;
import com.dianfei.dispatch.domain.VisitStop;

/**
 * VRPTW 评分（F4）：
 * 硬约束 = 超载 kg（×1000）+ 时间窗迟到分钟（×10）；
 * 软约束 = -总行驶米数（越短越优）。
 * 到达早于窗开则等待（不罚分），迟于窗关按分钟罚硬分。
 */
public class DispatchScoreCalculator implements EasyScoreCalculator<DispatchSolution, HardSoftLongScore> {

    private static final long OVERLOAD_WEIGHT = 1000L;
    private static final long LATE_WEIGHT = 10L;

    @Override
    public HardSoftLongScore calculateScore(DispatchSolution solution) {
        long hard = 0;
        long soft = 0;
        for (VehicleRoute vehicle : solution.getVehicles()) {
            double loadKg = 0;
            double posLng = vehicle.getStartLng();
            double posLat = vehicle.getStartLat();
            long clockMin = vehicle.getShiftStartMin();
            for (VisitStop stop : vehicle.getVisits()) {
                soft -= (long) GeoDistance.meters(posLng, posLat, stop.getLng(), stop.getLat());
                clockMin += GeoDistance.travelMinutes(posLng, posLat, stop.getLng(), stop.getLat());
                // 早到等待至窗开，迟到罚硬分
                clockMin = Math.max(clockMin, stop.getWindowStartMin());
                if (clockMin > stop.getWindowEndMin()) {
                    hard -= (clockMin - stop.getWindowEndMin()) * LATE_WEIGHT;
                }
                clockMin += stop.getServiceMin();
                loadKg += stop.getDemandKg();
                posLng = stop.getLng();
                posLat = stop.getLat();
            }
            if (loadKg > vehicle.getCapacityKg()) {
                hard -= (long) ((loadKg - vehicle.getCapacityKg()) * OVERLOAD_WEIGHT);
            }
        }
        return HardSoftLongScore.of(hard, soft);
    }
}

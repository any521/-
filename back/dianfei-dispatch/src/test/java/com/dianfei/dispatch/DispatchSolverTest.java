package com.dianfei.dispatch;

import com.dianfei.dispatch.domain.DispatchSolution;
import com.dianfei.dispatch.domain.VehicleRoute;
import com.dianfei.dispatch.domain.VisitStop;
import com.dianfei.dispatch.solver.GeoDistance;
import com.dianfei.dispatch.solver.VrptwSolverEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E6 求解内核验收：全部订单被指派、硬约束零违反（不超载/不迟到）、
 * 总里程不劣于最近邻贪心（对齐验收标准"优于 MVP 距离排序"）。
 */
class DispatchSolverTest {

    private final VrptwSolverEngine engine = new VrptwSolverEngine();

    @Test
    void solveAssignsAllVisitsWithoutHardViolationAndBeatsGreedy() {
        // 环形分布 8 个停靠点（经纬度按杭州城区尺度），预约窗宽松
        List<VisitStop> visits = new ArrayList<>();
        double baseLng = 120.15, baseLat = 30.25;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            visits.add(new VisitStop((long) (i + 1), "RO" + (i + 1), "addr" + i,
                    baseLng + Math.cos(angle) * 0.02, baseLat + Math.sin(angle) * 0.02,
                    20, 8 * 60, 18 * 60, 10));
        }
        // 单车 TSP-TW（TimefoldRoutePlanner 同口径）：与单车最近邻贪心公平对比
        List<VehicleRoute> vehicles = new ArrayList<>(List.of(
                new VehicleRoute(1L, 11L, "甲", 1000, baseLng, baseLat, 8 * 60)));

        DispatchSolution solved = engine.solve(vehicles, visits, Duration.ofSeconds(1));

        int assigned = solved.getVehicles().stream().mapToInt(v -> v.getVisits().size()).sum();
        assertThat(assigned).isEqualTo(8);
        assertThat(solved.getScore().hardScore()).isZero();
        // 软分 = -总里程（米）：不劣于最近邻贪心基线（MVP DistanceRoutePlanner 同构）
        double greedyMeters = greedyBaseline(baseLng, baseLat, visits);
        assertThat(-solved.getScore().softScore()).isLessThanOrEqualTo((long) greedyMeters);
    }

    @Test
    void multiVehicleSolveKeepsFeasibility() {
        // 双车分担 8 单：全部指派且容量/时间窗零硬违反
        List<VisitStop> visits = new ArrayList<>();
        double baseLng = 120.15, baseLat = 30.25;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            visits.add(new VisitStop((long) (i + 1), "RO" + (i + 1), "addr" + i,
                    baseLng + Math.cos(angle) * 0.02, baseLat + Math.sin(angle) * 0.02,
                    20, 8 * 60, 18 * 60, 10));
        }
        List<VehicleRoute> vehicles = new ArrayList<>(List.of(
                new VehicleRoute(1L, 11L, "甲", 100, baseLng, baseLat, 8 * 60),
                new VehicleRoute(2L, 12L, "乙", 100, baseLng, baseLat, 8 * 60)));

        DispatchSolution solved = engine.solve(vehicles, visits, Duration.ofSeconds(1));

        int assigned = solved.getVehicles().stream().mapToInt(v -> v.getVisits().size()).sum();
        assertThat(assigned).isEqualTo(8);
        assertThat(solved.getScore().hardScore()).isZero();
    }

    @Test
    void overloadedFleetReportsHardViolation() {
        // 总需求 60kg > 单车容量 30kg 且仅一辆车 → 必然超载，硬分为负（供降级判断）
        List<VisitStop> visits = List.of(
                new VisitStop(1L, "RO1", "a", 120.15, 30.25, 30, 0, 24 * 60, 10),
                new VisitStop(2L, "RO2", "b", 120.16, 30.26, 30, 0, 24 * 60, 10));
        List<VehicleRoute> vehicles = new ArrayList<>(List.of(
                new VehicleRoute(1L, 11L, "甲", 30, 120.15, 30.25, 8 * 60)));

        DispatchSolution solved = engine.solve(vehicles, new ArrayList<>(visits), Duration.ofSeconds(1));

        assertThat(solved.getScore().hardScore()).isNegative();
    }

    /** 最近邻贪心基线：单车从质心出发逐点取最近（MVP DistanceRoutePlanner 同构） */
    private double greedyBaseline(double startLng, double startLat, List<VisitStop> visits) {
        List<VisitStop> remaining = new ArrayList<>(visits);
        double total = 0, posLng = startLng, posLat = startLat;
        while (!remaining.isEmpty()) {
            VisitStop nearest = remaining.get(0);
            double best = Double.MAX_VALUE;
            for (VisitStop v : remaining) {
                double d = GeoDistance.meters(posLng, posLat, v.getLng(), v.getLat());
                if (d < best) {
                    best = d;
                    nearest = v;
                }
            }
            total += best;
            posLng = nearest.getLng();
            posLat = nearest.getLat();
            remaining.remove(nearest);
        }
        return total;
    }
}

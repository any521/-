package com.dianfei.dispatch.planner;

import com.dianfei.business.recycle.dispatch.DistanceRoutePlanner;
import com.dianfei.business.recycle.dispatch.RoutePlanner;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.dispatch.domain.DispatchSolution;
import com.dianfei.dispatch.domain.VehicleRoute;
import com.dianfei.dispatch.domain.VisitStop;
import com.dianfei.dispatch.solver.VrptwSolverEngine;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Timefold 路线排序（F4）：替换 B3 的 MVP 距离贪心成为 RoutePlanner 首选实现
 * （@Primary，RecycleDispatchService 注入点零改动）。单虚拟车 TSP-TW 求解
 * 上门顺序；求解异常或无坐标订单不足时自动降级 DistanceRoutePlanner，
 * 保证派单主链路不因求解器阻塞（00_overview AI 降级即解耦）。
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class TimefoldRoutePlanner implements RoutePlanner {

    private final VrptwSolverEngine solverEngine;
    private final DistanceRoutePlanner distanceRoutePlanner;
    private final ConfigService configService;

    @Override
    public List<RecycleOrder> plan(List<RecycleOrder> orders) {
        List<RecycleOrder> located = orders.stream()
                .filter(o -> o.getLng() != null && o.getLat() != null)
                .toList();
        if (located.size() < 2) {
            return distanceRoutePlanner.plan(orders);
        }
        try {
            List<VisitStop> visits = located.stream().map(this::toStop).toList();
            double centerLng = visits.stream().mapToDouble(VisitStop::getLng).average().orElse(0);
            double centerLat = visits.stream().mapToDouble(VisitStop::getLat).average().orElse(0);
            long shiftStart = shiftStartMin(located);
            // 单虚拟车（容量不设限）：只求上门顺序，员工均分由 RecycleDispatchService 连续切段
            VehicleRoute vehicle = new VehicleRoute(0L, null, "route", Double.MAX_VALUE,
                    centerLng, centerLat, shiftStart);
            DispatchSolution solved = solverEngine.solve(
                    new ArrayList<>(List.of(vehicle)), visits, solverLimit());

            Map<Long, RecycleOrder> byId = located.stream()
                    .collect(Collectors.toMap(RecycleOrder::getId, Function.identity()));
            List<RecycleOrder> route = new ArrayList<>(orders.size());
            solved.getVehicles().get(0).getVisits()
                    .forEach(stop -> route.add(byId.get(stop.getOrderId())));
            // 无坐标订单按预约时间垫后（与 MVP 行为一致）
            orders.stream()
                    .filter(o -> o.getLng() == null || o.getLat() == null)
                    .sorted(java.util.Comparator.comparing(RecycleOrder::getAppointTime))
                    .forEach(route::add);
            log.info("[F4] Timefold 路线排序完成：{} 单，score={}", route.size(), solved.getScore());
            return route;
        } catch (Exception e) {
            log.warn("[F4] Timefold 求解失败，降级距离贪心：{}", e.getMessage());
            return distanceRoutePlanner.plan(orders);
        }
    }

    private VisitStop toStop(RecycleOrder order) {
        long tolerance = configLong("dispatch.window-tolerance-min", 90);
        long serviceMin = configLong("dispatch.service-min", 10);
        long appointMin = minuteOfDay(order.getAppointTime());
        return new VisitStop(order.getId(), order.getOrderNo(), order.getAddress(),
                order.getLng().doubleValue(), order.getLat().doubleValue(),
                order.getEstWeight() == null ? 0 : order.getEstWeight().doubleValue(),
                Math.max(0, appointMin - tolerance), appointMin + tolerance, serviceMin);
    }

    /** 班次开始：最早预约前 1h 与配置班次起点取晚者 */
    private long shiftStartMin(List<RecycleOrder> orders) {
        long earliest = orders.stream().mapToLong(o -> minuteOfDay(o.getAppointTime())).min().orElse(480);
        long shiftConfig = configLong("dispatch.shift-start-hour", 8) * 60;
        return Math.max(shiftConfig, earliest - 60);
    }

    private Duration solverLimit() {
        return Duration.ofSeconds(configLong("dispatch.solver-seconds", 2));
    }

    private long configLong(String key, long def) {
        return Long.parseLong(configService.getValue(key, String.valueOf(def)));
    }

    private static long minuteOfDay(LocalDateTime time) {
        return time == null ? 480 : time.getHour() * 60L + time.getMinute();
    }
}

package com.dianfei.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.recycle.dispatch.DistanceRoutePlanner;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.dispatch.domain.DispatchSolution;
import com.dianfei.dispatch.domain.VehicleRoute;
import com.dianfei.dispatch.domain.VisitStop;
import com.dianfei.dispatch.dto.DispatchPlanDTO;
import com.dianfei.dispatch.solver.GeoDistance;
import com.dianfei.dispatch.solver.VrptwSolverEngine;
import com.dianfei.dispatch.vo.DispatchPlanVO;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F4 派单方案编排（E6）：当日待派单按片区 → 在岗回收员建车 → Timefold VRPTW
 * 多车求解（容量+时间窗）→ 方案预览 / apply 直接指派落库并站内信通知。
 * 求解失败自动降级 B3 距离贪心 + 连续切段（solver=DISTANCE_FALLBACK，主链路不阻塞）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchPlanService {

    private static final String SOLVER_TIMEFOLD = "TIMEFOLD_VRPTW";
    private static final String SOLVER_FALLBACK = "DISTANCE_FALLBACK";

    private final RecycleOrderMapper orderMapper;
    private final StaffService staffService;
    private final MessageSender messageSender;
    private final VrptwSolverEngine solverEngine;
    private final DistanceRoutePlanner distanceRoutePlanner;
    private final ConfigService configService;

    @Transactional(rollbackFor = Exception.class)
    public DispatchPlanVO plan(DispatchPlanDTO dto) {
        List<RecycleOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getStatus, OrderStatusEnum.PENDING.getCode())
                .isNull(RecycleOrder::getStaffId)
                .eq(dto.getAreaId() != null, RecycleOrder::getAreaId, dto.getAreaId())
                .ge(RecycleOrder::getAppointTime, dto.getDate().atStartOfDay())
                .lt(RecycleOrder::getAppointTime, dto.getDate().plusDays(1).atStartOfDay()));

        DispatchPlanVO vo = new DispatchPlanVO();
        vo.setDate(dto.getDate());
        vo.setSolver(SOLVER_TIMEFOLD);
        vo.setDegraded(false);
        vo.setTotalPending(pending.size());
        vo.setApplied(false);
        if (pending.isEmpty()) {
            vo.setTotalPlanned(0);
            return vo;
        }

        Map<Long, List<RecycleOrder>> byArea = pending.stream()
                .collect(Collectors.groupingBy(RecycleOrder::getAreaId));
        int planned = 0;
        for (Map.Entry<Long, List<RecycleOrder>> entry : byArea.entrySet()) {
            DispatchPlanVO.AreaPlan areaPlan = planArea(entry.getKey(), entry.getValue(), vo);
            vo.getAreas().add(areaPlan);
            planned += areaPlan.getRoutes().stream().mapToInt(r -> r.getStops().size()).sum();
        }
        vo.setTotalPlanned(planned);

        if (Boolean.TRUE.equals(dto.getApply())) {
            apply(vo, byArea);
            vo.setApplied(true);
        }
        return vo;
    }

    /** 单片区：在岗回收员建车 → 多车求解；异常降级距离贪心连续切段 */
    private DispatchPlanVO.AreaPlan planArea(Long areaId, List<RecycleOrder> orders, DispatchPlanVO vo) {
        DispatchPlanVO.AreaPlan areaPlan = new DispatchPlanVO.AreaPlan();
        areaPlan.setAreaId(areaId);
        List<BizStaff> recyclers = staffService.listOnDuty(areaId, StaffTypeEnum.RECYCLER);
        if (recyclers.isEmpty()) {
            areaPlan.setSkipReason("片区无在岗回收员，" + orders.size() + " 单滞留待派");
            return areaPlan;
        }
        try {
            solveArea(areaPlan, orders, recyclers);
        } catch (Exception e) {
            log.warn("[F4] 片区 {} Timefold 求解失败，降级距离贪心：{}", areaId, e.getMessage());
            vo.setSolver(SOLVER_FALLBACK);
            vo.setDegraded(true);
            fallbackArea(areaPlan, orders, recyclers);
        }
        return areaPlan;
    }

    private void solveArea(DispatchPlanVO.AreaPlan areaPlan, List<RecycleOrder> orders, List<BizStaff> recyclers) {
        List<RecycleOrder> located = orders.stream()
                .filter(o -> o.getLng() != null && o.getLat() != null).toList();
        List<RecycleOrder> unlocated = orders.stream()
                .filter(o -> o.getLng() == null || o.getLat() == null)
                .sorted(Comparator.comparing(RecycleOrder::getAppointTime)).toList();

        long tolerance = configLong("dispatch.window-tolerance-min", 90);
        long serviceMin = configLong("dispatch.service-min", 10);
        double capacityKg = configLong("dispatch.capacity-kg", 200);
        long shiftStart = configLong("dispatch.shift-start-hour", 8) * 60;

        List<VisitStop> visits = located.stream().map(o -> {
            long appointMin = minuteOfDay(o.getAppointTime());
            return new VisitStop(o.getId(), o.getOrderNo(), o.getAddress(),
                    o.getLng().doubleValue(), o.getLat().doubleValue(),
                    o.getEstWeight() == null ? 0 : o.getEstWeight().doubleValue(),
                    Math.max(0, appointMin - tolerance), appointMin + tolerance, serviceMin);
        }).toList();
        double centerLng = visits.stream().mapToDouble(VisitStop::getLng).average().orElse(0);
        double centerLat = visits.stream().mapToDouble(VisitStop::getLat).average().orElse(0);

        List<VehicleRoute> vehicles = recyclers.stream()
                .map(s -> new VehicleRoute(s.getId(), s.getUserId(), s.getStaffName(),
                        capacityKg, centerLng, centerLat, shiftStart))
                .collect(Collectors.toCollection(ArrayList::new));

        DispatchSolution solved = visits.isEmpty()
                ? new DispatchSolution(vehicles, List.of())
                : solverEngine.solve(vehicles, visits,
                        Duration.ofSeconds(configLong("dispatch.solver-seconds", 2)));

        Map<Long, RecycleOrder> orderById = orders.stream()
                .collect(Collectors.toMap(RecycleOrder::getId, o -> o));
        List<DispatchPlanVO.Route> routes = new ArrayList<>();
        for (VehicleRoute vehicle : solved.getVehicles()) {
            routes.add(toRouteVo(vehicle, orderById));
        }
        // 无坐标订单轮询追加到最短路线尾部（与 MVP 垫后语义一致）
        for (RecycleOrder order : unlocated) {
            DispatchPlanVO.Route shortest = routes.stream()
                    .min(Comparator.comparingInt(r -> r.getStops().size())).orElseThrow();
            shortest.getStops().add(toStopVo(shortest.getStops().size() + 1, order, null));
            shortest.setTotalKg(shortest.getTotalKg().add(estKg(order)));
        }
        routes.removeIf(r -> r.getStops().isEmpty());
        areaPlan.setRoutes(routes);
    }

    /** 降级：距离贪心排序 + 连续切段均分（B3 RecycleDispatchService 同构） */
    private void fallbackArea(DispatchPlanVO.AreaPlan areaPlan, List<RecycleOrder> orders, List<BizStaff> recyclers) {
        List<RecycleOrder> route = distanceRoutePlanner.plan(orders);
        int perStaff = (route.size() + recyclers.size() - 1) / recyclers.size();
        List<DispatchPlanVO.Route> routes = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            int staffIdx = Math.min(i / perStaff, recyclers.size() - 1);
            if (staffIdx >= routes.size()) {
                BizStaff staff = recyclers.get(staffIdx);
                DispatchPlanVO.Route r = new DispatchPlanVO.Route();
                r.setStaffId(staff.getId());
                r.setStaffName(staff.getStaffName());
                r.setTotalKg(BigDecimal.ZERO);
                r.setTotalKm(BigDecimal.ZERO);
                routes.add(r);
            }
            DispatchPlanVO.Route r = routes.get(staffIdx);
            RecycleOrder order = route.get(i);
            r.getStops().add(toStopVo(r.getStops().size() + 1, order, null));
            r.setTotalKg(r.getTotalKg().add(estKg(order)));
        }
        areaPlan.setRoutes(routes);
    }

    /** 指派落库：方案中的 stop 逐条回填 staff_id 并通知（幂等：仅未指派单） */
    private void apply(DispatchPlanVO vo, Map<Long, List<RecycleOrder>> byArea) {
        Map<Long, RecycleOrder> orderById = byArea.values().stream().flatMap(List::stream)
                .collect(Collectors.toMap(RecycleOrder::getId, o -> o));
        Map<Long, BizStaff> staffCache = vo.getAreas().stream()
                .flatMap(a -> a.getRoutes().stream())
                .map(DispatchPlanVO.Route::getStaffId).distinct()
                .map(staffService::getById)
                .collect(Collectors.toMap(BizStaff::getId, s -> s));
        for (DispatchPlanVO.AreaPlan area : vo.getAreas()) {
            for (DispatchPlanVO.Route route : area.getRoutes()) {
                BizStaff staff = staffCache.get(route.getStaffId());
                for (DispatchPlanVO.Stop stop : route.getStops()) {
                    RecycleOrder order = orderById.get(Long.valueOf(stop.getOrderId()));
                    if (order == null || order.getStaffId() != null) {
                        continue;
                    }
                    order.setStaffId(route.getStaffId());
                    orderMapper.updateById(order);
                    messageSender.send(staff.getUserId(), MessageSender.TYPE_DISPATCHED,
                            "新回收单指派", "回收单 " + order.getOrderNo() + "（顺路第 "
                                    + stop.getSeq() + " 单）已指派给您，请及时接单");
                }
            }
        }
    }

    /** 车辆路线 → VO：按评分器同口径推演 ETA 与里程 */
    private DispatchPlanVO.Route toRouteVo(VehicleRoute vehicle, Map<Long, RecycleOrder> orderById) {
        DispatchPlanVO.Route r = new DispatchPlanVO.Route();
        r.setStaffId(vehicle.getStaffId());
        r.setStaffName(vehicle.getStaffName());
        double totalKg = 0;
        double totalMeters = 0;
        double posLng = vehicle.getStartLng();
        double posLat = vehicle.getStartLat();
        long clockMin = vehicle.getShiftStartMin();
        int seq = 1;
        for (VisitStop stop : vehicle.getVisits()) {
            totalMeters += GeoDistance.meters(posLng, posLat, stop.getLng(), stop.getLat());
            clockMin += GeoDistance.travelMinutes(posLng, posLat, stop.getLng(), stop.getLat());
            clockMin = Math.max(clockMin, stop.getWindowStartMin());
            r.getStops().add(toStopVo(seq++, orderById.get(stop.getOrderId()),
                    String.format("%02d:%02d", clockMin / 60 % 24, clockMin % 60)));
            clockMin += stop.getServiceMin();
            totalKg += stop.getDemandKg();
            posLng = stop.getLng();
            posLat = stop.getLat();
        }
        r.setTotalKg(BigDecimal.valueOf(totalKg).setScale(1, RoundingMode.HALF_UP));
        r.setTotalKm(BigDecimal.valueOf(totalMeters / 1000).setScale(2, RoundingMode.HALF_UP));
        return r;
    }

    private DispatchPlanVO.Stop toStopVo(int seq, RecycleOrder order, String eta) {
        DispatchPlanVO.Stop stop = new DispatchPlanVO.Stop();
        stop.setSeq(seq);
        stop.setOrderId(String.valueOf(order.getId()));
        stop.setOrderNo(order.getOrderNo());
        stop.setAddress(order.getAddress());
        stop.setAppointTime(order.getAppointTime());
        stop.setEta(eta);
        stop.setEstWeightKg(estKg(order));
        return stop;
    }

    private static BigDecimal estKg(RecycleOrder order) {
        return order.getEstWeight() == null ? BigDecimal.ZERO : order.getEstWeight();
    }

    private long configLong(String key, long def) {
        return Long.parseLong(configService.getValue(key, String.valueOf(def)));
    }

    private static long minuteOfDay(LocalDateTime time) {
        return time == null ? 480 : time.getHour() * 60L + time.getMinute();
    }
}

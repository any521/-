package com.dianfei.business.staff.service;

import com.dianfei.business.kitchen.entity.KitchenTask;
import com.dianfei.business.kitchen.service.KitchenTaskService;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.service.RepairOrderService;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.vo.RoutePlanVO;
import com.dianfei.business.staff.vo.RouteStopVO;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.enums.RepairStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 员工智能路线（G11）：预约时间贪心排序（algorithm=APPOINT_TIME），
 * 订单池 = 今日回收（指派给我 + 本片区待接单）+ 今日垃圾投放（指派给我）+ 今日维修（指派给我）；
 * 距离 Haversine（复用 GeoDistance 算法思路，lng/lat 任一为空该段 0），
 * totalMin = totalKm/15*60 + 停靠点数*20（15km/h 均速 + 每单 20 分钟服务）。
 * 无订单返回空 stops（不报错，前端已处理空态）。
 */
@Service
@RequiredArgsConstructor
public class StaffRouteService {

    /** 算法标识：预约时间贪心 */
    public static final String ALGORITHM_APPOINT_TIME = "APPOINT_TIME";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 城市道路均速 km/h（估算用） */
    private static final double AVG_SPEED_KMH = 15.0;
    /** 每停靠点服务时长 分钟 */
    private static final int SERVICE_MIN_PER_STOP = 20;
    private static final double EARTH_RADIUS_M = 6371000.0;

    private final StaffService staffService;
    private final RecycleOrderService recycleOrderService;
    private final KitchenTaskService kitchenTaskService;
    private final RepairOrderService repairOrderService;

    public RoutePlanVO todayRoute(Long userId) {
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        List<Candidate> candidates = new ArrayList<>();

        // 回收：指派给我的（今天 0 点起含未来预约；appoint_time 为空=尽快上门也纳入，避免「已接单但路线无单」；
        // 已完成/取消/异常不进入路线，避免完成订单后导航残留旧单）+ 本片区待接单（status=0）
        recycleOrderService.lambdaQuery()
                .and(w -> w.isNull(RecycleOrder::getAppointTime)
                        .or(ww -> ww.ge(RecycleOrder::getAppointTime, dayStart)))
                .and(w -> w.eq(RecycleOrder::getStaffId, staff.getId())
                        .lt(RecycleOrder::getStatus, OrderStatusEnum.COMPLETED.getCode())
                        .or(ww -> ww.eq(RecycleOrder::getAreaId, staff.getAreaId())
                                .eq(RecycleOrder::getStatus, OrderStatusEnum.PENDING.getCode())))
                .list()
                .forEach(o -> candidates.add(new Candidate(
                        String.valueOf(o.getId()), o.getAddress(),
                        o.getAppointTime(), TIME_FMT.format(o.getAppointTime()),
                        o.getLng(), o.getLat(), o.getStatus() >= 3)));

        // 垃圾投放：指派给我的（task_date 今日；已完成的不再进路线；排序按 0 点视为最早班次，展示仅日期）
        kitchenTaskService.lambdaQuery()
                .eq(KitchenTask::getStaffId, staff.getId())
                .eq(KitchenTask::getTaskDate, today)
                .eq(KitchenTask::getStatus, KitchenTask.STATUS_PENDING)
                .list()
                .forEach(t -> candidates.add(new Candidate(
                        String.valueOf(t.getId()),
                        "片区" + t.getAreaId() + " 集中投放点",
                        t.getTaskDate().atStartOfDay(), t.getTaskDate().toString(),
                        null, null,
                        t.getStatus() == KitchenTask.STATUS_FINISHED)));

        // 维修：指派给我的（appoint_start 今天 0 点起含未来，空值也纳入；已完成/取消/转回收不再进路线）
        repairOrderService.lambdaQuery()
                .eq(RepairOrder::getStaffId, staff.getId())
                .lt(RepairOrder::getStatus, RepairStatusEnum.COMPLETED.getCode())
                .and(w -> w.isNull(RepairOrder::getAppointStart)
                        .or(ww -> ww.ge(RepairOrder::getAppointStart, dayStart)))
                .list()
                .forEach(r -> candidates.add(new Candidate(
                        String.valueOf(r.getId()), r.getAddress(),
                        r.getAppointStart(), TIME_FMT.format(r.getAppointStart()),
                        r.getLng(), r.getLat(), r.getStatus() >= 3)));

        candidates.sort(Comparator.comparing(Candidate::sortTime));
        return buildPlan(candidates);
    }

    /** 按预约时间升序编号 seq，相邻点 Haversine 求段里程，累加 totalKm（1 位小数） */
    private RoutePlanVO buildPlan(List<Candidate> candidates) {
        List<RouteStopVO> stops = new ArrayList<>(candidates.size());
        BigDecimal totalKm = BigDecimal.ZERO;
        BigDecimal prevLng = null;
        BigDecimal prevLat = null;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            BigDecimal segKm = BigDecimal.ZERO;
            if (i > 0 && c.lng() != null && c.lat() != null && prevLng != null && prevLat != null) {
                segKm = BigDecimal.valueOf(meters(prevLng.doubleValue(), prevLat.doubleValue(),
                                c.lng().doubleValue(), c.lat().doubleValue()) / 1000.0)
                        .setScale(1, RoundingMode.HALF_UP);
            }
            stops.add(new RouteStopVO(c.orderId(), i + 1, c.address(), "预约客户",
                    c.appointText(), segKm, c.done()));
            totalKm = totalKm.add(segKm);
            prevLng = c.lng();
            prevLat = c.lat();
        }
        int totalMin = stops.isEmpty() ? 0
                : (int) Math.round(totalKm.doubleValue() / AVG_SPEED_KMH * 60 + stops.size() * SERVICE_MIN_PER_STOP);
        RoutePlanVO vo = new RoutePlanVO();
        vo.setTotalKm(totalKm);
        vo.setTotalMin(totalMin);
        vo.setAlgorithm(ALGORITHM_APPOINT_TIME);
        vo.setStops(stops);
        return vo;
    }

    /** Haversine 球面距离（米），与 dianfei-dispatch GeoDistance 同算法 */
    private static double meters(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /** 停靠点候选（三单源合并后统一排序） */
    private record Candidate(String orderId, String address, LocalDateTime sortTime,
                             String appointText, BigDecimal lng, BigDecimal lat, boolean done) {
    }
}

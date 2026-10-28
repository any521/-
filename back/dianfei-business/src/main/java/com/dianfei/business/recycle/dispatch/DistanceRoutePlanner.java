package com.dianfei.business.recycle.dispatch;

import com.dianfei.business.recycle.entity.RecycleOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 距离贪心路线排序（B3 MVP）：以最早预约单为起点，逐步取最近邻；
 * 无经纬度的订单按预约时间垫后。F4 Timefold 实现就绪后作为其降级兜底。
 */
@Component
public class DistanceRoutePlanner implements RoutePlanner {

    @Override
    public List<RecycleOrder> plan(List<RecycleOrder> orders) {
        List<RecycleOrder> located = new ArrayList<>(orders.stream()
                .filter(o -> o.getLng() != null && o.getLat() != null)
                .sorted(Comparator.comparing(RecycleOrder::getAppointTime))
                .toList());
        List<RecycleOrder> unlocated = orders.stream()
                .filter(o -> o.getLng() == null || o.getLat() == null)
                .sorted(Comparator.comparing(RecycleOrder::getAppointTime))
                .toList();

        List<RecycleOrder> route = new ArrayList<>(orders.size());
        if (!located.isEmpty()) {
            // 起点：最早预约单
            RecycleOrder current = located.remove(0);
            route.add(current);
            while (!located.isEmpty()) {
                RecycleOrder finalCurrent = current;
                RecycleOrder nearest = located.stream()
                        .min(Comparator.comparingDouble(o -> distance(finalCurrent, o)))
                        .orElseThrow();
                located.remove(nearest);
                route.add(nearest);
                current = nearest;
            }
        }
        route.addAll(unlocated);
        return route;
    }

    /** 平面近似距离（片区尺度足够，Haversine 简化）：经纬度差平方和 */
    private double distance(RecycleOrder a, RecycleOrder b) {
        double dLng = a.getLng().subtract(b.getLng()).doubleValue();
        double dLat = a.getLat().subtract(b.getLat()).doubleValue();
        return dLng * dLng + dLat * dLat;
    }
}

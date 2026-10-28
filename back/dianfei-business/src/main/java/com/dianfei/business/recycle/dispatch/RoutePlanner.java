package com.dianfei.business.recycle.dispatch;

import com.dianfei.business.recycle.entity.RecycleOrder;

import java.util.List;

/**
 * 派单路线规划抽象（B3）：入参为同片区当日待派订单，出参为按上门顺序排序的列表。
 *
 * <p>MVP 由 {@link DistanceRoutePlanner} 按距离贪心排序；
 * F4 就绪后新增实现改调 Track E 的 `/api/dispatch/plan`（Timefold VRPTW），
 * 调用失败时降级回距离排序（对齐 trackB B3 / 00_overview AI 降级即解耦）。
 */
public interface RoutePlanner {

    /**
     * 对同片区订单做路线排序
     *
     * @param orders 待派订单（同一片区、同一天）
     * @return 按建议上门顺序排序的订单列表
     */
    List<RecycleOrder> plan(List<RecycleOrder> orders);
}

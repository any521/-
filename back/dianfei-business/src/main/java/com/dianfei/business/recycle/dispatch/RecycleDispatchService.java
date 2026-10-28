package com.dianfei.business.recycle.dispatch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回收派单编排（B3）：按片区聚合指定日期未指派待接单 → RoutePlanner 路线排序 →
 * 按路线顺序轮询批量指派本片区在岗回收员（staff_id 回填、状态保持 0 待员工确认接单），并站内信通知。
 * 定时任务与后台手动触发共用本入口，幂等可重跑（只处理 staff_id 为空的单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleDispatchService {

    private final RecycleOrderMapper orderMapper;
    private final RoutePlanner routePlanner;
    private final StaffService staffService;
    private final MessageSender messageSender;

    /** 派发指定日期全部片区，返回成功指派单数 */
    @Transactional
    public int dispatchDate(LocalDate date) {
        List<RecycleOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getStatus, OrderStatusEnum.PENDING.getCode())
                .isNull(RecycleOrder::getStaffId)
                .ge(RecycleOrder::getAppointTime, date.atStartOfDay())
                .lt(RecycleOrder::getAppointTime, date.plusDays(1).atStartOfDay()));
        if (pending.isEmpty()) {
            return 0;
        }
        Map<Long, List<RecycleOrder>> byArea = pending.stream()
                .collect(Collectors.groupingBy(RecycleOrder::getAreaId));
        int assigned = 0;
        for (Map.Entry<Long, List<RecycleOrder>> entry : byArea.entrySet()) {
            assigned += dispatchArea(entry.getKey(), entry.getValue());
        }
        log.info("[Dispatch] {} 派单完成：待派 {} 单，成功指派 {} 单", date, pending.size(), assigned);
        return assigned;
    }

    /** 单片区派发：路线排序后按顺序轮询指派在岗回收员（连续路线段尽量归同一员工） */
    private int dispatchArea(Long areaId, List<RecycleOrder> orders) {
        List<BizStaff> recyclers = staffService.listOnDuty(areaId, StaffTypeEnum.RECYCLER);
        if (recyclers.isEmpty()) {
            log.warn("[Dispatch] 片区 {} 无在岗回收员，{} 单滞留待派", areaId, orders.size());
            return 0;
        }
        List<RecycleOrder> route = routePlanner.plan(orders);
        // 按路线顺序均分：每员工领取连续路线段，保证顺路
        int perStaff = (route.size() + recyclers.size() - 1) / recyclers.size();
        int assigned = 0;
        for (int i = 0; i < route.size(); i++) {
            RecycleOrder order = route.get(i);
            BizStaff staff = recyclers.get(Math.min(i / perStaff, recyclers.size() - 1));
            order.setStaffId(staff.getId());
            orderMapper.updateById(order);
            assigned++;
            messageSender.send(staff.getUserId(), MessageSender.TYPE_DISPATCHED,
                    "新回收单指派", "回收单 " + order.getOrderNo() + "（顺路第 " + (i % perStaff + 1)
                            + " 单）已指派给您，请及时接单");
        }
        return assigned;
    }
}

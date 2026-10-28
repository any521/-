package com.dianfei.business.repair.linkage;

import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.recycle.dispatch.RecycleDispatchService;
import com.dianfei.business.recycle.service.RecycleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 维修域到回收域的正式适配器。
 * 保留历史 Bean 名称以兼容现有配置和测试，但不再直写回收表；建单和派单均复用回收域服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StubRecycleOrderLinkage implements RecycleOrderLinkage {

    private final RecycleOrderService recycleOrderService;
    private final RecycleDispatchService recycleDispatchService;

    @Override
    public Long createFromRepair(RepairOrder repairOrder) {
        Long recycleId = recycleOrderService.createFromRepair(repairOrder);
        recycleDispatchService.dispatchDate(java.time.LocalDate.now().plusDays(1));
        log.info("[Linkage] repairOrder={} -> formal recycleOrder={}", repairOrder.getOrderNo(), recycleId);
        return recycleId;
    }
}

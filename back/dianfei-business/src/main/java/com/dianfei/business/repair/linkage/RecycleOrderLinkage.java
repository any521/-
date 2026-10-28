package com.dianfei.business.repair.linkage;

import com.dianfei.business.repair.entity.RepairOrder;

/**
 * 维修转回收联动契约（C2，Sprint 0 冻结）：
 * 不可修复件由维修工单一键生成 biz_recycle_order（回填 repair_ref 双向关联）。
 *
 * <p>Track B 回收订单服务就绪后，由 B 的 RecycleOrderService 提供真实实现
 * （复用 B 的派单/通知链路）；当前使用 {@link StubRecycleOrderLinkage} 直写冻结表占位。
 * 调用方保证在同一事务内回填 repair 侧 recycle_ref，失败整体回滚不产生脏数据（对齐 11.2 高风险项）。
 */
public interface RecycleOrderLinkage {

    /**
     * 由不可修复维修单生成回收工单
     *
     * @return 新回收工单 ID（biz_recycle_order.id）
     */
    Long createFromRepair(RepairOrder repairOrder);
}

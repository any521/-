package com.dianfei.business.repair.linkage;

import com.dianfei.business.message.MessageSender;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.linkage.entity.LinkedRecycleOrder;
import com.dianfei.business.repair.linkage.mapper.LinkedRecycleOrderMapper;
import com.dianfei.business.repair.service.RepairOrderService;
import com.dianfei.business.repair.vo.RepairTraceVO;
import com.dianfei.business.repair.vo.RepairTransferVO;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.common.enums.RepairStatusEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 维修转回收联动编排（C2，平台核心）：
 * 不可修复件一键生成 biz_recycle_order，同事务回填 recycle_ref 双向关联，状态机 2检修中 → 5已转回收。
 *
 * <p>状态机分支（对齐 11.2 高风险项，单测全覆盖）：
 * <ul>
 *   <li>已转回收 + 已回填 recycle_ref → 幂等重入，直接返回既有联动结果（不重复建单）</li>
 *   <li>非 2检修中 → 2003 订单状态不允许</li>
 *   <li>repairable != 2（未判定/可修复） → 拒绝转回收</li>
 *   <li>非本单技术员 → 403</li>
 *   <li>联动建单/回填任一失败 → @Transactional 整体回滚补偿，不产生脏数据
 *       （B 切换远程真实服务后，此处需追加反向补偿：删除/作废已生成回收单）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairLinkageService {

    private final RepairOrderService repairOrderService;
    private final RecycleOrderLinkage recycleOrderLinkage;
    private final LinkedRecycleOrderMapper recycleOrderMapper;
    private final MessageSender messageSender;

    /**
     * 不可修复件一键转回收（限本单技术员）：
     * 生成回收工单 → 回填 recycle_ref → 状态置 5已转回收，同事务原子完成
     */
    @Transactional
    public RepairTransferVO transferToRecycle(Long orderId, Long staffUserId) {
        BizStaff staff = repairOrderService.requireTechnician(staffUserId);
        RepairOrder order = repairOrderService.requireOrder(orderId);
        if (!staff.getId().equals(order.getStaffId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "工单未指派给当前技术员");
        }
        // 幂等重入：已转回收直接返回既有联动结果
        if (RepairStatusEnum.TRANSFERRED.getCode().equals(order.getStatus()) && order.getRecycleRef() != null) {
            return toTransferVO(order, recycleOrderMapper.selectById(order.getRecycleRef()));
        }
        RepairOrderService.requireStatus(order, RepairStatusEnum.CHECKING);
        if (order.getRepairable() == null || order.getRepairable() != 2) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "仅判定不可修复的工单可转回收");
        }

        Long recycleId = recycleOrderLinkage.createFromRepair(order);
        order.setRecycleRef(recycleId);
        order.setStatus(RepairStatusEnum.TRANSFERRED.getCode());
        order.setFinishTime(LocalDateTime.now());
        repairOrderService.updateById(order);

        LinkedRecycleOrder recycle = recycleOrderMapper.selectById(recycleId);
        messageSender.send(order.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                "维修单已转回收", "工单 " + order.getOrderNo() + " 判定不可修复，已生成回收工单 "
                        + (recycle == null ? recycleId : recycle.getOrderNo()) + "，回收员将尽快上门");
        log.info("[Linkage] transfer repairOrder={} -> recycleId={}", order.getOrderNo(), recycleId);
        return toTransferVO(order, recycle);
    }

    /** 双单溯源（归档永久留存）：维修单 + 联动回收单双向关联视图；userId 非空时校验归属 */
    public RepairTraceVO trace(Long orderId, Long userId) {
        RepairOrder order = repairOrderService.requireOrder(orderId);
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        RepairTraceVO vo = new RepairTraceVO();
        vo.setRepairOrderId(String.valueOf(order.getId()));
        vo.setRepairOrderNo(order.getOrderNo());
        vo.setRepairStatus(order.getStatus());
        vo.setRepairStatusDesc(RepairOrderService.statusDesc(order.getStatus()));
        vo.setRepairable(order.getRepairable());
        vo.setFaultType(order.getFaultType());
        vo.setRepairCreateTime(order.getCreateTime());
        vo.setRepairFinishTime(order.getFinishTime());
        if (order.getRecycleRef() != null) {
            LinkedRecycleOrder recycle = recycleOrderMapper.selectById(order.getRecycleRef());
            if (recycle != null) {
                vo.setRecycleOrderId(String.valueOf(recycle.getId()));
                vo.setRecycleOrderNo(recycle.getOrderNo());
                vo.setRecycleStatus(recycle.getStatus());
                vo.setRecycleCreateTime(recycle.getCreateTime());
                // 双向校验：biz_recycle_order.repair_ref 必须指回本维修单
                vo.setBidirectional(order.getId().equals(recycle.getRepairRef()));
            }
        }
        return vo;
    }

    /** 员工查看双单溯源（在岗员工均可查看）：本片区且已指派给本人/待接单的维修单。 */
    public RepairTraceVO traceForStaff(Long orderId, Long staffUserId) {
        BizStaff staff = repairOrderService.requireStaff(staffUserId);
        RepairOrder order = repairOrderService.requireOrder(orderId);
        if (!Objects.equals(staff.getAreaId(), order.getAreaId())
                || (order.getStaffId() != null && !staff.getId().equals(order.getStaffId()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该维修溯源");
        }
        return trace(orderId, order.getUserId());
    }

    private RepairTransferVO toTransferVO(RepairOrder order, LinkedRecycleOrder recycle) {
        RepairTransferVO vo = new RepairTransferVO();
        vo.setRepairOrderId(String.valueOf(order.getId()));
        vo.setRepairOrderNo(order.getOrderNo());
        vo.setRepairStatus(order.getStatus());
        if (recycle != null) {
            vo.setRecycleOrderId(String.valueOf(recycle.getId()));
            vo.setRecycleOrderNo(recycle.getOrderNo());
            vo.setRecycleStatus(recycle.getStatus());
        } else if (order.getRecycleRef() != null) {
            vo.setRecycleOrderId(String.valueOf(order.getRecycleRef()));
        }
        return vo;
    }
}

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C2 转回收联动状态机全分支单测（对齐 11.2 高风险项验收）：
 * 成功联动 / 幂等重入 / 状态不允许 / 可修复性拦截 / 越权 / 联动失败回滚不落脏数据 / 双单溯源
 */
@ExtendWith(MockitoExtension.class)
class RepairLinkageServiceTest {

    @Mock
    private RepairOrderService repairOrderService;
    @Mock
    private RecycleOrderLinkage recycleOrderLinkage;
    @Mock
    private LinkedRecycleOrderMapper recycleOrderMapper;
    @Mock
    private MessageSender messageSender;

    @InjectMocks
    private RepairLinkageService repairLinkageService;

    private BizStaff technician;
    private RepairOrder order;

    @BeforeEach
    void setUp() {
        technician = new BizStaff();
        technician.setId(10L);
        technician.setUserId(100L);

        order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RP1");
        order.setUserId(200L);
        order.setStaffId(10L);
        order.setAreaId(5L);
        order.setStatus(RepairStatusEnum.CHECKING.getCode());
        order.setRepairable(2);

        lenient().when(repairOrderService.requireTechnician(100L)).thenReturn(technician);
        lenient().when(repairOrderService.requireOrder(1L)).thenReturn(order);
    }

    @Test
    @DisplayName("成功联动：2检修中+不可修复 → 生成回收单、回填 recycle_ref、状态置 5")
    void transferSuccess() {
        when(recycleOrderLinkage.createFromRepair(order)).thenReturn(99L);
        LinkedRecycleOrder recycle = new LinkedRecycleOrder();
        recycle.setId(99L);
        recycle.setOrderNo("RO99");
        recycle.setStatus(LinkedRecycleOrder.STATUS_PENDING);
        recycle.setRepairRef(1L);
        when(recycleOrderMapper.selectById(99L)).thenReturn(recycle);

        RepairTransferVO vo = repairLinkageService.transferToRecycle(1L, 100L);

        assertThat(order.getRecycleRef()).isEqualTo(99L);
        assertThat(order.getStatus()).isEqualTo(RepairStatusEnum.TRANSFERRED.getCode());
        assertThat(order.getFinishTime()).isNotNull();
        assertThat(vo.getRecycleOrderNo()).isEqualTo("RO99");
        assertThat(vo.getRepairStatus()).isEqualTo(RepairStatusEnum.TRANSFERRED.getCode());
        verify(repairOrderService).updateById(order);
        verify(messageSender).send(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("幂等重入：已转回收+已回填 → 返回既有联动结果，不重复建单")
    void transferIdempotent() {
        order.setStatus(RepairStatusEnum.TRANSFERRED.getCode());
        order.setRecycleRef(99L);
        LinkedRecycleOrder recycle = new LinkedRecycleOrder();
        recycle.setId(99L);
        recycle.setOrderNo("RO99");
        when(recycleOrderMapper.selectById(99L)).thenReturn(recycle);

        RepairTransferVO vo = repairLinkageService.transferToRecycle(1L, 100L);

        assertThat(vo.getRecycleOrderNo()).isEqualTo("RO99");
        verify(recycleOrderLinkage, never()).createFromRepair(any());
        verify(repairOrderService, never()).updateById(any());
    }

    @Test
    @DisplayName("状态分支：非 2检修中（0/1/3/4）一律 2003")
    void transferWrongStatus() {
        for (RepairStatusEnum s : new RepairStatusEnum[]{RepairStatusEnum.PENDING,
                RepairStatusEnum.ACCEPTED, RepairStatusEnum.COMPLETED, RepairStatusEnum.CANCELLED}) {
            order.setStatus(s.getCode());
            assertThatThrownBy(() -> repairLinkageService.transferToRecycle(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getResultCode())
                    .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
        }
        verify(recycleOrderLinkage, never()).createFromRepair(any());
    }

    @Test
    @DisplayName("可修复性分支：未判定(0/null)/可修复(1) 拒绝转回收")
    void transferNotUnrepairable() {
        for (Integer repairable : new Integer[]{0, 1, null}) {
            order.setRepairable(repairable);
            assertThatThrownBy(() -> repairLinkageService.transferToRecycle(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可修复");
        }
        verify(recycleOrderLinkage, never()).createFromRepair(any());
    }

    @Test
    @DisplayName("越权分支：非本单技术员 403")
    void transferNotOwnOrder() {
        order.setStaffId(999L);
        assertThatThrownBy(() -> repairLinkageService.transferToRecycle(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.FORBIDDEN);
        verify(recycleOrderLinkage, never()).createFromRepair(any());
    }

    @Test
    @DisplayName("失败回滚：联动建单抛异常 → 维修单未被改写（同事务整体回滚，不落脏数据）")
    void transferRollbackOnLinkageFailure() {
        when(recycleOrderLinkage.createFromRepair(order)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> repairLinkageService.transferToRecycle(1L, 100L))
                .isInstanceOf(RuntimeException.class);

        verify(repairOrderService, never()).updateById(any());
        verify(messageSender, never()).send(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("双单溯源：recycle.repair_ref 指回维修单 → bidirectional=true")
    void traceBidirectional() {
        order.setStatus(RepairStatusEnum.TRANSFERRED.getCode());
        order.setRecycleRef(99L);
        LinkedRecycleOrder recycle = new LinkedRecycleOrder();
        recycle.setId(99L);
        recycle.setOrderNo("RO99");
        recycle.setStatus(LinkedRecycleOrder.STATUS_PENDING);
        recycle.setRepairRef(1L);
        when(recycleOrderMapper.selectById(99L)).thenReturn(recycle);

        RepairTraceVO vo = repairLinkageService.trace(1L, 200L);

        assertThat(vo.getRepairOrderNo()).isEqualTo("RP1");
        assertThat(vo.getRecycleOrderNo()).isEqualTo("RO99");
        assertThat(vo.getBidirectional()).isTrue();
    }

    @Test
    @DisplayName("双单溯源：未联动的维修单只返回维修侧")
    void traceWithoutRecycle() {
        RepairTraceVO vo = repairLinkageService.trace(1L, null);
        assertThat(vo.getRecycleOrderId()).isNull();
        assertThat(vo.getBidirectional()).isNull();
    }

    @Test
    @DisplayName("双单溯源：非本人工单 403")
    void traceForbidden() {
        assertThatThrownBy(() -> repairLinkageService.trace(1L, 777L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.FORBIDDEN);
    }
}

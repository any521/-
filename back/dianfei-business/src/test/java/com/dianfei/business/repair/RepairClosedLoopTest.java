package com.dianfei.business.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonRuleSaveDTO;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.service.CarbonRuleService;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.message.service.MessageService;
import com.dianfei.business.repair.dto.RepairOrderCreateDTO;
import com.dianfei.business.repair.dto.RepairReportDTO;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.linkage.RecycleOrderLinkage;
import com.dianfei.business.repair.linkage.RepairLinkageService;
import com.dianfei.business.repair.linkage.StubRecycleOrderLinkage;
import com.dianfei.business.repair.linkage.entity.LinkedRecycleOrder;
import com.dianfei.business.repair.linkage.mapper.LinkedRecycleOrderMapper;
import com.dianfei.business.repair.service.RepairOrderService;
import com.dianfei.business.repair.vo.RepairOrderVO;
import com.dianfei.business.repair.vo.RepairTraceVO;
import com.dianfei.business.repair.vo.RepairTransferVO;
import com.dianfei.business.staff.dto.AreaSaveDTO;
import com.dianfei.business.staff.dto.StaffAuditDTO;
import com.dianfei.business.staff.dto.StaffCreateDTO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.RepairStatusEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C1/C2 维修闭环验收单测（H2 + 真实碳账本，对齐 trackC 验收标准）：
 * 可修复闭环：预约(F2 预诊)→接单→检修→报告→完成→积分到账（REPAIR_REPLACE 规则、按件计分）/
 * 转回收闭环：判定不可修复→一键转回收→回收单 repair_ref 双向可溯→推送片区回收员→幂等重入 /
 * 联动失败真实事务回滚不落脏数据 / 用户取消
 */
@SpringBootTest
class RepairClosedLoopTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(130_000L);
    /** 联动通道故障注入开关（rollback 用例置 true） */
    private static final AtomicBoolean FAIL_LINKAGE = new AtomicBoolean(false);

    @Autowired
    private RepairOrderService orderService;
    @Autowired
    private RepairLinkageService linkageService;
    @Autowired
    private LinkedRecycleOrderMapper recycleOrderMapper;
    @Autowired
    private CarbonRuleService ruleService;
    @Autowired
    private CarbonLedgerOps ledger;
    @Autowired
    private CarbonLogMapper carbonLogMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private AreaService areaService;
    @Autowired
    private StaffService staffService;

    /** 联动通道可注入故障：默认透传 Stub 直写，FAIL_LINKAGE 时抛异常验证同事务回滚 */
    @TestConfiguration
    static class FailableLinkageConfig {
        @Bean
        @Primary
        RecycleOrderLinkage failableLinkage(StubRecycleOrderLinkage stub) {
            return order -> {
                if (FAIL_LINKAGE.get()) {
                    throw new IllegalStateException("联动通道故障（测试注入）");
                }
                return stub.createFromRepair(order);
            };
        }
    }

    @AfterEach
    void resetFailFlag() {
        FAIL_LINKAGE.set(false);
    }

    @Test
    void repairableClosedLoopGrantsPoints() {
        BizArea area = area("维修片区");
        BizStaff technician = onDutyTechnician(area.getId());
        Long userId = USER_SEQ.incrementAndGet();
        seedRepairRule();

        // 预约下单：F2 预诊建议随单返回（Mock：照明类 → 建议维修）
        RepairOrderVO created = orderService.create(userId, createDTO(area.getId(), "照明灯具", "客厅灯不亮"));
        Long orderId = Long.valueOf(created.getId());
        assertThat(created.getStatus()).isEqualTo(RepairStatusEnum.PENDING.getCode());
        assertThat(created.getDiagnose()).isNotNull();
        assertThat(created.getDiagnose().getSuggestion()).isEqualTo("REPAIR");

        // 接单→上门检修：本片区技术员待接单可见
        assertThat(orderService.pagePending(technician.getUserId(), 1, 10).getList())
                .extracting(RepairOrderVO::getId).contains(created.getId());
        orderService.accept(orderId, technician.getUserId());
        orderService.startCheck(orderId, technician.getUserId());
        assertThat(messageService.pageMine(userId, MessageSender.TYPE_DISPATCHED, null, 1, 10)
                .getTotal()).isEqualTo(1);

        // 检修报告：可修复 + 配件 2 种共 3 件 → 3已完成，按件发放 30×3=90 分、减排 5×3=15 kgCO2
        long balanceBefore = ledger.getBalance(userId);
        orderService.submitReport(orderId, technician.getUserId(), reportDTO(1,
                part("LED 驱动电源", 2), part("灯管", 1)));

        RepairOrderVO detail = orderService.detail(orderId, userId);
        assertThat(detail.getStatus()).isEqualTo(RepairStatusEnum.COMPLETED.getCode());
        assertThat(detail.getParts()).hasSize(2);
        assertThat(ledger.getBalance(userId)).isEqualTo(balanceBefore + 90);

        // 流水命中 REPAIR_REPLACE 规则，carbon_reduced 正确；积分到账站内信落库
        CarbonLog log = carbonLogMapper.selectOne(new LambdaQueryWrapper<CarbonLog>()
                .eq(CarbonLog::getOrderId, orderId).eq(CarbonLog::getOrderType, "REPAIR"));
        assertThat(log).isNotNull();
        assertThat(log.getRuleCode()).isEqualTo("REPAIR_REPLACE");
        assertThat(log.getPoints()).isEqualTo(90L);
        assertThat(log.getCarbonReduced()).isEqualByComparingTo("15");
        assertThat(messageService.pageMine(userId, MessageSender.TYPE_POINTS_ARRIVED, null, 1, 10)
                .getTotal()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void unrepairableTransfersToRecycleWithBidirectionalTrace() {
        BizArea area = area("联动片区");
        BizStaff technician = onDutyTechnician(area.getId());
        BizStaff recycler = onDutyRecycler(area.getId());
        Long userId = USER_SEQ.incrementAndGet();
        seedRepairRule();

        // 老旧插排：Mock 预诊建议转回收（C2 强制链路）
        RepairOrderVO created = orderService.create(userId, createDTO(area.getId(), "老旧插排", "插排烧焦冒烟"));
        Long orderId = Long.valueOf(created.getId());
        assertThat(created.getDiagnose().getSuggestion()).isEqualTo("RECYCLE");

        orderService.accept(orderId, technician.getUserId());
        orderService.startCheck(orderId, technician.getUserId());
        // 判定不可修复：保持 2检修中，不发积分
        orderService.submitReport(orderId, technician.getUserId(), reportDTO(2));
        assertThat(orderService.getById(orderId).getStatus()).isEqualTo(RepairStatusEnum.CHECKING.getCode());
        assertThat(ledger.getBalance(userId)).isZero();

        // 一键转回收：生成回收单、回填 recycle_ref、状态置 5，同事务原子完成
        RepairTransferVO transfer = linkageService.transferToRecycle(orderId, technician.getUserId());
        RepairOrder after = orderService.getById(orderId);
        assertThat(after.getStatus()).isEqualTo(RepairStatusEnum.TRANSFERRED.getCode());
        assertThat(after.getRecycleRef()).isNotNull();
        LinkedRecycleOrder recycle = recycleOrderMapper.selectById(after.getRecycleRef());
        assertThat(recycle.getOrderNo()).startsWith("RO");
        assertThat(recycle.getRepairRef()).isEqualTo(orderId);
        assertThat(recycle.getStatus()).isEqualTo(LinkedRecycleOrder.STATUS_PENDING);

        // 推送片区在岗回收员（复用 B 派单/通知占位通道）
        assertThat(messageService.pageMine(recycler.getUserId(), MessageSender.TYPE_DISPATCHED, null, 1, 10)
                .getTotal()).isEqualTo(1);

        // 幂等重入：不重复建单，返回既有回收单
        RepairTransferVO again = linkageService.transferToRecycle(orderId, technician.getUserId());
        assertThat(again.getRecycleOrderId()).isEqualTo(transfer.getRecycleOrderId());
        assertThat(recycleOrderMapper.selectCount(new LambdaQueryWrapper<LinkedRecycleOrder>()
                .eq(LinkedRecycleOrder::getRepairRef, orderId))).isEqualTo(1);

        // 双单溯源归档：repair_ref 双向校验通过（用户视角可查）
        RepairTraceVO trace = linkageService.trace(orderId, userId);
        assertThat(trace.getRecycleOrderNo()).isEqualTo(recycle.getOrderNo());
        assertThat(trace.getBidirectional()).isTrue();
    }

    @Test
    void linkageFailureRollsBackWithoutDirtyData() {
        BizArea area = area("回滚片区");
        BizStaff technician = onDutyTechnician(area.getId());
        Long userId = USER_SEQ.incrementAndGet();

        RepairOrderVO created = orderService.create(userId, createDTO(area.getId(), "老旧插排", "线路老化"));
        Long orderId = Long.valueOf(created.getId());
        orderService.accept(orderId, technician.getUserId());
        orderService.startCheck(orderId, technician.getUserId());
        orderService.submitReport(orderId, technician.getUserId(), reportDTO(2));

        // 注入联动通道故障：真实事务回滚，维修单未被改写、无残留回收单
        FAIL_LINKAGE.set(true);
        assertThatThrownBy(() -> linkageService.transferToRecycle(orderId, technician.getUserId()))
                .isInstanceOf(IllegalStateException.class);

        RepairOrder after = orderService.getById(orderId);
        assertThat(after.getStatus()).isEqualTo(RepairStatusEnum.CHECKING.getCode());
        assertThat(after.getRecycleRef()).isNull();
        assertThat(recycleOrderMapper.selectCount(new LambdaQueryWrapper<LinkedRecycleOrder>()
                .eq(LinkedRecycleOrder::getRepairRef, orderId))).isZero();

        // 故障恢复后重试成功（补偿闭环）
        FAIL_LINKAGE.set(false);
        RepairTransferVO transfer = linkageService.transferToRecycle(orderId, technician.getUserId());
        assertThat(transfer.getRecycleOrderId()).isNotBlank();
        assertThat(orderService.getById(orderId).getStatus())
                .isEqualTo(RepairStatusEnum.TRANSFERRED.getCode());
    }

    @Test
    void userCancelOnlyBeforeChecking() {
        BizArea area = area("取消片区");
        BizStaff technician = onDutyTechnician(area.getId());
        Long userId = USER_SEQ.incrementAndGet();

        // 待接单可取消
        RepairOrderVO first = orderService.create(userId, createDTO(area.getId(), "小家电", "电水壶不加热"));
        orderService.cancel(Long.valueOf(first.getId()), userId, "计划有变");
        assertThat(orderService.getById(Long.valueOf(first.getId())).getStatus())
                .isEqualTo(RepairStatusEnum.CANCELLED.getCode());

        // 检修开始后不可取消 → 2003
        RepairOrderVO second = orderService.create(userId, createDTO(area.getId(), "小家电", "风扇异响"));
        Long secondId = Long.valueOf(second.getId());
        orderService.accept(secondId, technician.getUserId());
        orderService.startCheck(secondId, technician.getUserId());
        assertThatThrownBy(() -> orderService.cancel(secondId, userId, "不修了"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
    }

    // ---------- 测试辅助 ----------

    /** 维修替代换新规则：30 分/件、5 kgCO2/件（orderType+category 幂等 upsert） */
    private void seedRepairRule() {
        CarbonRuleSaveDTO rule = new CarbonRuleSaveDTO();
        rule.setRuleCode("REPAIR_REPLACE");
        rule.setOrderType("REPAIR");
        rule.setCategory("REPAIR_REPLACE");
        rule.setPointsPerBag(new BigDecimal("30"));
        rule.setCarbonPerBag(new BigDecimal("5"));
        rule.setStatus(1);
        ruleService.save(rule);
    }

    private BizArea area(String name) {
        AreaSaveDTO dto = new AreaSaveDTO();
        dto.setAreaName(name + "-" + USER_SEQ.incrementAndGet());
        dto.setCleanThreshold(100);
        dto.setStatus(1);
        return areaService.saveArea(dto);
    }

    private BizStaff onDutyTechnician(Long areaId) {
        return onDutyStaff(areaId, StaffTypeEnum.TECHNICIAN, "技术员");
    }

    private BizStaff onDutyRecycler(Long areaId) {
        return onDutyStaff(areaId, StaffTypeEnum.RECYCLER, "回收员");
    }

    private BizStaff onDutyStaff(Long areaId, StaffTypeEnum type, String namePrefix) {
        long staffUserId = USER_SEQ.incrementAndGet();
        StaffCreateDTO dto = new StaffCreateDTO();
        dto.setUserId(staffUserId);
        dto.setStaffName(namePrefix + staffUserId);
        dto.setPhone("13900000000");
        dto.setStaffType(type.getCode());
        dto.setAreaId(areaId);
        BizStaff staff = staffService.create(dto);
        StaffAuditDTO audit = new StaffAuditDTO();
        audit.setApproved(true);
        staffService.audit(staff.getId(), audit);
        return staffService.getById(staff.getId());
    }

    private static RepairOrderCreateDTO createDTO(Long areaId, String faultType, String faultDesc) {
        RepairOrderCreateDTO dto = new RepairOrderCreateDTO();
        dto.setAreaId(areaId);
        dto.setFaultType(faultType);
        dto.setFaultDesc(faultDesc);
        dto.setAddress("绿园小区 2-3-402");
        dto.setAppointStart(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        dto.setAppointEnd(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
        return dto;
    }

    private static RepairReportDTO reportDTO(int repairable, RepairReportDTO.PartItem... parts) {
        RepairReportDTO dto = new RepairReportDTO();
        dto.setRepairable(repairable);
        dto.setReportContent(repairable == 1 ? "更换驱动电源与灯管后恢复" : "内部线路老化短路，无维修价值");
        dto.setScienceNote("科普：老化线路易引发短路，建议定期检查用电环境");
        dto.setParts(parts.length == 0 ? null : List.of(parts));
        return dto;
    }

    private static RepairReportDTO.PartItem part(String name, int quantity) {
        RepairReportDTO.PartItem item = new RepairReportDTO.PartItem();
        item.setPartName(name);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("25.00"));
        return item;
    }
}

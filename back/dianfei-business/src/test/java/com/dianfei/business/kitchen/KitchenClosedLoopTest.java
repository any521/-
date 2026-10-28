package com.dianfei.business.kitchen;

import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonRuleSaveDTO;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.service.CarbonRuleService;
import com.dianfei.business.kitchen.dto.KitchenReportAuditDTO;
import com.dianfei.business.kitchen.dto.KitchenReportCreateDTO;
import com.dianfei.business.kitchen.dto.KitchenTaskFinishDTO;
import com.dianfei.business.kitchen.dto.VehicleSaveDTO;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.entity.KitchenTask;
import com.dianfei.business.kitchen.entity.Vehicle;
import com.dianfei.business.kitchen.service.KitchenReportService;
import com.dianfei.business.kitchen.service.KitchenTaskService;
import com.dianfei.business.kitchen.service.VehicleService;
import com.dianfei.business.map.entity.BizStation;
import com.dianfei.business.map.mapper.BizStationMapper;
import com.dianfei.business.staff.dto.AreaSaveDTO;
import com.dianfei.business.staff.dto.StaffAuditDTO;
import com.dianfei.business.staff.dto.StaffCreateDTO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B4 垃圾投放闭环单测（H2）：时段校验 / 距垃圾回收站 50m 硬校验 / 一律待审核 + 员工端审核发分 /
 * 聚合任务+派车建议+uk 幂等 / 归档仅对已通过单幂等兜底 / 归属与改派校验（对齐 trackB 验收标准）
 */
@SpringBootTest
@Import(KitchenClosedLoopTest.FixedClockConfig.class)
class KitchenClosedLoopTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(70_000L);
    private static final AtomicLong VEHICLE_SEQ = new AtomicLong(1000L);

    /** 测试厨余站坐标：上报坐标与其一致时距离为 0（50m 内） */
    private static final BigDecimal STATION_LNG = new BigDecimal("120.100000");
    private static final BigDecimal STATION_LAT = new BigDecimal("30.200000");

    @Autowired
    private KitchenReportService reportService;
    @Autowired
    private KitchenTaskService taskService;
    @Autowired
    private VehicleService vehicleService;
    @Autowired
    private BizStationMapper stationMapper;
    @Autowired
    private AreaService areaService;
    @Autowired
    private StaffService staffService;
    @Autowired
    private CarbonRuleService ruleService;
    @Autowired
    private CarbonLedgerOps ledger;
    @Autowired
    private CarbonAccountMapper accountMapper;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void initRuleAndStation() {
        // upsert 语义可重复执行：厨余 2 分/袋、0.3 kgCO2/袋
        CarbonRuleSaveDTO rule = new CarbonRuleSaveDTO();
        rule.setRuleCode("KITCHEN_DEFAULT");
        rule.setOrderType("KITCHEN");
        rule.setCategory("DEFAULT");
        rule.setPointsPerBag(new BigDecimal("2"));
        rule.setCarbonPerBag(new BigDecimal("0.3"));
        rule.setStatus(1);
        ruleService.save(rule);

        BizStation station = new BizStation();
        station.setName("测试厨余站");
        station.setStationType("KITCHEN");
        station.setLng(STATION_LNG);
        station.setLat(STATION_LAT);
        station.setAddress("测试路 1 号");
        station.setStatus(1);
        stationMapper.insert(station);
    }

    @Test
    void reportRejectedOutsideWindow() {
        BizArea area = area("时段片区", 100, null);
        clock.set(LocalDateTime.of(2026, 8, 20, 10, 0));
        assertThatThrownBy(() -> reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("垃圾投放仅限");
        // 窗口内正常入库（一律待审核）
        clock.set(LocalDateTime.of(2026, 8, 20, 21, 0));
        KitchenReport report = reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 2));
        assertThat(report.getStatus()).isEqualTo(KitchenReport.STATUS_PENDING);
    }

    @Test
    void reportRejectedBeyondStation() {
        BizArea area = area("超距片区", 100, null);
        clock.set(LocalDateTime.of(2026, 8, 20, 21, 0));
        // 偏离站点约 0.9km（+0.01 度）→ 拒绝投放
        assertThatThrownBy(() -> reportService.report(USER_SEQ.incrementAndGet(),
                reportDTO(area.getId(), 2, STATION_LNG.add(new BigDecimal("0.01")), STATION_LAT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("50 米内");
    }

    @Test
    void reportPendingUntilAudited() {
        BizArea area = area("审核片区", 100, null);
        clock.set(LocalDateTime.of(2026, 8, 21, 20, 30));
        // 一律待审核（户均风控已由人工审核替代）
        KitchenReport pending = reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 3));
        assertThat(pending.getStatus()).isEqualTo(KitchenReport.STATUS_PENDING);
        BizStaff sanitation = onDutyStaff(area.getId(), 2);

        // 驳回 → 3 已驳回、不发分
        reportService.audit(pending.getId(), sanitation.getUserId(), auditDTO(false, "照片不清晰"));
        assertThat(reportService.getById(pending.getId()).getStatus()).isEqualTo(KitchenReport.STATUS_REJECTED);
        assertThat(ledger.getBalance(pending.getUserId())).isZero();
        // 已审核单不可重复审核
        assertThatThrownBy(() -> reportService.audit(pending.getId(), sanitation.getUserId(), auditDTO(true, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);

        // 通过 → 1 已通过 + 立即发分（3 袋 × 2 分）
        KitchenReport approved = reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 3));
        reportService.audit(approved.getId(), sanitation.getUserId(), auditDTO(true, null));
        assertThat(reportService.getById(approved.getId()).getStatus()).isEqualTo(KitchenReport.STATUS_APPROVED);
        assertThat(ledger.getBalance(approved.getUserId())).isEqualTo(6);
        // 重复审核（幂等保护）仍拦
        assertThatThrownBy(() -> reportService.audit(approved.getId(), sanitation.getUserId(), auditDTO(true, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void auditGuardsAreaAndDuty() {
        BizArea area = area("守卫片区", 100, null);
        clock.set(LocalDateTime.of(2026, 8, 21, 21, 0));
        KitchenReport report = reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 2));
        // 跨片区在岗员工不可审核
        BizArea other = area("外区", 100, null);
        BizStaff outsider = onDutyStaff(other.getId(), 2);
        assertThatThrownBy(() -> reportService.audit(report.getId(), outsider.getUserId(), auditDTO(true, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本片区");
        // 未上岗用户不可审核
        long plainUserId = USER_SEQ.incrementAndGet();
        assertThatThrownBy(() -> reportService.audit(report.getId(), plainUserId, auditDTO(true, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在岗员工");
    }

    @Test
    void aggregateCreatesTaskWithVehicleSuggestionIdempotently() {
        LocalDate reportDate = LocalDate.of(2026, 8, 22);
        BizArea reached = area("达标片区", 10, null);
        BizArea below = area("未达标片区", 10, null);
        Vehicle small = vehicle(reached.getId(), 8);
        Vehicle fit = vehicle(reached.getId(), 15);

        clock.set(reportDate.atTime(21, 0));
        for (int i = 0; i < 3; i++) {
            reportService.report(USER_SEQ.incrementAndGet(), reportDTO(reached.getId(), 4)); // 共 12 袋
        }
        reportService.report(USER_SEQ.incrementAndGet(), reportDTO(below.getId(), 2));

        taskService.aggregate(reportDate);
        KitchenTask task = findTask(reached.getId(), reportDate.plusDays(1));
        assertThat(task).isNotNull();
        assertThat(task.getTotalBags()).isEqualTo(12);
        // 派车建议：容量够装的最小车（15 > 12 > 8）
        assertThat(task.getVehicleId()).isEqualTo(fit.getId());
        assertThat(task.getVehicleId()).isNotEqualTo(small.getId());
        // 未达阈值片区不建任务
        assertThat(findTask(below.getId(), reportDate.plusDays(1))).isNull();
        // uk 幂等：重跑不重复建任务
        taskService.aggregate(reportDate);
        assertThat(taskService.lambdaQuery().eq(KitchenTask::getAreaId, reached.getId())
                .eq(KitchenTask::getTaskDate, reportDate.plusDays(1)).count()).isEqualTo(1);
    }

    @Test
    void finishSettlesApprovedOnlyIdempotently() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        BizArea area = area("归档片区", 5, null);
        long approvedUser = USER_SEQ.incrementAndGet();
        long pendingUser = USER_SEQ.incrementAndGet();

        clock.set(reportDate.atTime(22, 0));
        KitchenReport approved = reportService.report(approvedUser, reportDTO(area.getId(), 3));  // 待审核
        KitchenReport pending = reportService.report(pendingUser, reportDTO(area.getId(), 2));    // 待审核
        BizStaff sanitation = onDutyStaff(area.getId(), 2);
        reportService.audit(approved.getId(), sanitation.getUserId(), auditDTO(true, null));      // 审核通过即发分
        taskService.aggregate(reportDate);
        KitchenTask task = findTask(area.getId(), reportDate.plusDays(1));

        clock.set(reportDate.plusDays(1).atTime(5, 30));
        KitchenTask finished = taskService.finish(task.getId(), sanitation.getUserId(), finishDTO());
        assertThat(finished.getStatus()).isEqualTo(KitchenTask.STATUS_FINISHED);
        assertThat(finished.getStaffId()).isEqualTo(sanitation.getId());
        assertThat(finished.getTotalWeight()).isEqualByComparingTo("36.50");

        // 已通过单发分到账：3 袋 × 2 分（审核通过时已发，归档幂等不重发）
        assertThat(ledger.getBalance(approvedUser)).isEqualTo(6);
        // 待审核单归档时仍不发分（等待审核）
        assertThat(ledger.getBalance(pendingUser)).isZero();
        assertThat(reportService.getById(pending.getId()).getStatus()).isEqualTo(KitchenReport.STATUS_PENDING);

        // 重复归档抵 2003
        assertThatThrownBy(() -> taskService.finish(task.getId(), sanitation.getUserId(), finishDTO()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
    }

    @Test
    void finishGuardsStaffTypeAndArea() {
        LocalDate reportDate = LocalDate.of(2026, 8, 24);
        BizArea area = area("守卫片区", 1, null);
        clock.set(reportDate.atTime(20, 10));
        reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 2));
        taskService.aggregate(reportDate);
        KitchenTask task = findTask(area.getId(), reportDate.plusDays(1));

        // 回收员不可归档
        BizStaff recycler = onDutyStaff(area.getId(), 1);
        assertThatThrownBy(() -> taskService.finish(task.getId(), recycler.getUserId(), finishDTO()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅环卫员");
        // 跨片区环卫员不可归档
        BizArea other = area("其他片区", 1, null);
        BizStaff outsider = onDutyStaff(other.getId(), 2);
        assertThatThrownBy(() -> taskService.finish(task.getId(), outsider.getUserId(), finishDTO()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本片区");
        // 本片区环卫员任务列表可见
        BizStaff sanitation = onDutyStaff(area.getId(), 2);
        assertThat(taskService.myTasks(sanitation.getUserId()))
                .extracting(KitchenTask::getId).contains(task.getId());
    }

    @Test
    void reassignRulesAndVehicleUpsert() {
        LocalDate reportDate = LocalDate.of(2026, 8, 25);
        BizArea area = area("改派片区", 1, null);
        clock.set(reportDate.atTime(21, 30));
        reportService.report(USER_SEQ.incrementAndGet(), reportDTO(area.getId(), 3));
        taskService.aggregate(reportDate);
        KitchenTask task = findTask(area.getId(), reportDate.plusDays(1));

        // 车辆 upsert：同车牌重复保存不新增
        Vehicle vehicle = vehicle(area.getId(), 20);
        VehicleSaveDTO dup = vehicleDTO(vehicle.getVehicleNo(), area.getId(), 25);
        Vehicle updated = vehicleService.saveByVehicleNo(dup);
        assertThat(updated.getId()).isEqualTo(vehicle.getId());
        assertThat(updated.getCapacity()).isEqualTo(25);

        // 改派限本片区环卫员
        BizArea other = area("外区", 1, null);
        BizStaff outsider = onDutyStaff(other.getId(), 2);
        assertThatThrownBy(() -> taskService.reassign(task.getId(), null, outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本片区环卫员");
        BizStaff sanitation = onDutyStaff(area.getId(), 2);
        KitchenTask reassigned = taskService.reassign(task.getId(), vehicle.getId(), sanitation.getId());
        assertThat(reassigned.getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(reassigned.getStaffId()).isEqualTo(sanitation.getId());

        // 已归档任务不可改派
        clock.set(reportDate.plusDays(1).atTime(5, 0));
        taskService.finish(task.getId(), sanitation.getUserId(), finishDTO());
        assertThatThrownBy(() -> taskService.reassign(task.getId(), null, sanitation.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
    }

    // ---------- 测试辅助 ----------

    private BizArea area(String name, Integer cleanThreshold, Integer householdCount) {
        AreaSaveDTO dto = new AreaSaveDTO();
        dto.setAreaName(name + "-" + USER_SEQ.incrementAndGet());
        dto.setCleanThreshold(cleanThreshold);
        dto.setHouseholdCount(householdCount);
        dto.setStatus(1);
        return areaService.saveArea(dto);
    }

    /** 建员工并审核上岗（staffType：1 回收员 2 环卫员） */
    private BizStaff onDutyStaff(Long areaId, int staffType) {
        long userId = USER_SEQ.incrementAndGet();
        StaffCreateDTO dto = new StaffCreateDTO();
        dto.setUserId(userId);
        dto.setStaffName("员工" + userId);
        dto.setPhone("13900000000");
        dto.setStaffType(staffType);
        dto.setAreaId(areaId);
        BizStaff staff = staffService.create(dto);
        StaffAuditDTO audit = new StaffAuditDTO();
        audit.setApproved(true);
        staffService.audit(staff.getId(), audit);
        return staffService.getById(staff.getId());
    }

    private Vehicle vehicle(Long areaId, int capacity) {
        return vehicleService.saveByVehicleNo(vehicleDTO("川A" + VEHICLE_SEQ.incrementAndGet(), areaId, capacity));
    }

    private static VehicleSaveDTO vehicleDTO(String vehicleNo, Long areaId, int capacity) {
        VehicleSaveDTO dto = new VehicleSaveDTO();
        dto.setVehicleNo(vehicleNo);
        dto.setAreaId(areaId);
        dto.setCapacity(capacity);
        dto.setStatus(1);
        return dto;
    }

    private static KitchenReportCreateDTO reportDTO(Long areaId, int bagCount) {
        return reportDTO(areaId, bagCount, STATION_LNG, STATION_LAT);
    }

    private static KitchenReportCreateDTO reportDTO(Long areaId, int bagCount, BigDecimal lng, BigDecimal lat) {
        KitchenReportCreateDTO dto = new KitchenReportCreateDTO();
        dto.setAreaId(areaId);
        dto.setPointName("1 号投放点");
        dto.setBagCount(bagCount);
        dto.setLng(lng);
        dto.setLat(lat);
        return dto;
    }

    private static KitchenReportAuditDTO auditDTO(boolean pass, String remark) {
        KitchenReportAuditDTO dto = new KitchenReportAuditDTO();
        dto.setPass(pass);
        dto.setRemark(remark);
        return dto;
    }

    private static KitchenTaskFinishDTO finishDTO() {
        KitchenTaskFinishDTO dto = new KitchenTaskFinishDTO();
        dto.setTotalWeight(new BigDecimal("36.50"));
        dto.setVehicleNo("川A00001");
        dto.setDestination("市厨余处理厂");
        dto.setProofUrl("https://minio/proof.jpg");
        return dto;
    }

    private KitchenTask findTask(Long areaId, LocalDate taskDate) {
        return taskService.getOne(new LambdaQueryWrapper<KitchenTask>()
                .eq(KitchenTask::getAreaId, areaId).eq(KitchenTask::getTaskDate, taskDate));
    }

    private CarbonAccount findAccount(long userId) {
        return accountMapper.selectOne(
                new LambdaQueryWrapper<CarbonAccount>().eq(CarbonAccount::getUserId, userId));
    }

    /** 可拨动测试时钟：覆盖 20-23 点投放窗口内外场景 */
    static final class MutableClock extends Clock {
        private final ZoneId zone = ZoneId.systemDefault();
        private volatile Instant instant = Instant.now();

        void set(LocalDateTime dateTime) {
            this.instant = dateTime.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}

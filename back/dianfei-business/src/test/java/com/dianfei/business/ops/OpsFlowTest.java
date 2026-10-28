package com.dianfei.business.ops;

import com.dianfei.business.ops.dto.LeaveAuditDTO;
import com.dianfei.business.ops.dto.LeaveCreateDTO;
import com.dianfei.business.ops.dto.MaintenanceSaveDTO;
import com.dianfei.business.ops.dto.ScheduleSaveDTO;
import com.dianfei.business.ops.entity.DeviceMaintenance;
import com.dianfei.business.ops.entity.LeaveRequest;
import com.dianfei.business.ops.entity.Schedule;
import com.dianfei.business.ops.service.DeviceMaintenanceService;
import com.dianfei.business.ops.service.LeaveService;
import com.dianfei.business.ops.service.ScheduleService;
import com.dianfei.business.staff.dto.AreaSaveDTO;
import com.dianfei.business.staff.dto.StaffAuditDTO;
import com.dianfei.business.staff.dto.StaffCreateDTO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C4 排班运维闭环单测（H2）：
 * 排班冲突拦截 / 跨片区支援校验 / 请假区间禁排 /
 * 请假审核通过自动移除区间排班 / 重叠请假拦截 / 运维登记
 */
@SpringBootTest
class OpsFlowTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(90_000L);
    private static final LocalDate D1 = LocalDate.of(2026, 8, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 11);
    private static final LocalDate D3 = LocalDate.of(2026, 8, 12);

    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private LeaveService leaveService;
    @Autowired
    private DeviceMaintenanceService maintenanceService;
    @Autowired
    private StaffService staffService;
    @Autowired
    private AreaService areaService;

    @Test
    void duplicateScheduleBlocked() {
        BizStaff staff = onDutyStaff();
        scheduleService.create(scheduleDTO(staff.getId(), D1, Schedule.SHIFT_DAY, Schedule.BIZ_NORMAL, null));

        assertThatThrownBy(() -> scheduleService.create(
                scheduleDTO(staff.getId(), D1, Schedule.SHIFT_DAY, Schedule.BIZ_HOLIDAY, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有排班");
        // 同日不同班次可排
        Schedule night = scheduleService.create(
                scheduleDTO(staff.getId(), D1, Schedule.SHIFT_NIGHT, Schedule.BIZ_NORMAL, null));
        assertThat(night.getAreaId()).isEqualTo(staff.getAreaId());
    }

    @Test
    void supportScheduleRequiresEnabledTargetArea() {
        BizStaff staff = onDutyStaff();
        assertThatThrownBy(() -> scheduleService.create(
                scheduleDTO(staff.getId(), D1, Schedule.SHIFT_DAY, Schedule.BIZ_SUPPORT, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标片区");

        BizArea support = area("支援片区");
        Schedule schedule = scheduleService.create(
                scheduleDTO(staff.getId(), D1, Schedule.SHIFT_DAY, Schedule.BIZ_SUPPORT, support.getId()));
        assertThat(schedule.getSupportAreaId()).isEqualTo(support.getId());
    }

    @Test
    void approvedLeaveRemovesSchedulesAndBlocksNewOnes() {
        BizStaff staff = onDutyStaff();
        scheduleService.create(scheduleDTO(staff.getId(), D1, Schedule.SHIFT_DAY, Schedule.BIZ_NORMAL, null));
        scheduleService.create(scheduleDTO(staff.getId(), D2, Schedule.SHIFT_NIGHT, Schedule.BIZ_NORMAL, null));

        LeaveRequest leave = leaveService.submit(staff.getUserId(), leaveDTO(D1, D2, "家中有事"));
        assertThat(leave.getStatus()).isEqualTo(LeaveRequest.STATUS_PENDING);
        // 待审核时排班仍在
        assertThat(scheduleService.listStaffSchedules(staff.getId(), D1, D3)).hasSize(2);

        LeaveAuditDTO audit = new LeaveAuditDTO();
        audit.setApproved(true);
        audit.setAuditRemark("同意");
        LeaveRequest approved = leaveService.audit(leave.getId(), 1L, audit);

        assertThat(approved.getStatus()).isEqualTo(LeaveRequest.STATUS_APPROVED);
        // 区间内排班被移除
        assertThat(scheduleService.listStaffSchedules(staff.getId(), D1, D3)).isEmpty();
        // 请假区间内禁止再排班
        assertThatThrownBy(() -> scheduleService.create(
                scheduleDTO(staff.getId(), D2, Schedule.SHIFT_DAY, Schedule.BIZ_NORMAL, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请假区间");
        // 区间外正常排班
        assertThat(scheduleService.create(
                scheduleDTO(staff.getId(), D3, Schedule.SHIFT_DAY, Schedule.BIZ_NORMAL, null))).isNotNull();
        // 已审核单不可重复审核
        assertThatThrownBy(() -> leaveService.audit(leave.getId(), 1L, audit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核");
    }

    @Test
    void overlappedLeaveRejectedAndRejectKeepsSchedules() {
        BizStaff staff = onDutyStaff();
        scheduleService.create(scheduleDTO(staff.getId(), D1, Schedule.SHIFT_DAY, Schedule.BIZ_NORMAL, null));
        LeaveRequest first = leaveService.submit(staff.getUserId(), leaveDTO(D1, D2, "体检"));

        // 区间重叠的待审单拦截
        assertThatThrownBy(() -> leaveService.submit(staff.getUserId(), leaveDTO(D2, D3, "旅行")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有待审核或已批准");

        LeaveAuditDTO reject = new LeaveAuditDTO();
        reject.setApproved(false);
        reject.setAuditRemark("人手不足");
        LeaveRequest rejected = leaveService.audit(first.getId(), 1L, reject);

        assertThat(rejected.getStatus()).isEqualTo(LeaveRequest.STATUS_REJECTED);
        // 驳回不动排班
        assertThat(scheduleService.listStaffSchedules(staff.getId(), D1, D3)).hasSize(1);
        // 驳回后同区间可重新申请
        assertThat(leaveService.submit(staff.getUserId(), leaveDTO(D1, D2, "复诊"))).isNotNull();
    }

    @Test
    void maintenanceRegisterAndPage() {
        DeviceMaintenance saved = maintenanceService.register(1L, maintenanceDTO("川A88888", "季度保养+年检"));
        assertThat(saved.getOperatorId()).isEqualTo(1L);

        var page = maintenanceService.pageAdmin(DeviceMaintenance.TARGET_VEHICLE, "川A88888", 1, 10);
        assertThat(page.getList()).extracting(DeviceMaintenance::getContent).contains("季度保养+年检");

        MaintenanceSaveDTO bad = maintenanceDTO("X1", "内容");
        bad.setTargetType(9);
        assertThatThrownBy(() -> maintenanceService.register(1L, bad))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类型不合法");
    }

    // ---------- 测试辅助 ----------

    private BizArea area(String name) {
        AreaSaveDTO dto = new AreaSaveDTO();
        dto.setAreaName(name);
        dto.setStatus(1);
        return areaService.saveArea(dto);
    }

    /** 建片区+员工并审核上岗 */
    private BizStaff onDutyStaff() {
        long userId = USER_SEQ.incrementAndGet();
        BizArea area = area("片区-" + userId);
        StaffCreateDTO dto = new StaffCreateDTO();
        dto.setUserId(userId);
        dto.setStaffName("员工" + userId);
        dto.setPhone("13800000000");
        dto.setStaffType(1);
        dto.setAreaId(area.getId());
        BizStaff staff = staffService.create(dto);
        StaffAuditDTO audit = new StaffAuditDTO();
        audit.setApproved(true);
        staffService.audit(staff.getId(), audit);
        return staffService.getById(staff.getId());
    }

    private static ScheduleSaveDTO scheduleDTO(Long staffId, LocalDate date, int shift, int bizType, Long supportAreaId) {
        ScheduleSaveDTO dto = new ScheduleSaveDTO();
        dto.setStaffId(staffId);
        dto.setWorkDate(date);
        dto.setShiftType(shift);
        dto.setBizType(bizType);
        dto.setSupportAreaId(supportAreaId);
        return dto;
    }

    private static LeaveCreateDTO leaveDTO(LocalDate start, LocalDate end, String reason) {
        LeaveCreateDTO dto = new LeaveCreateDTO();
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason(reason);
        return dto;
    }

    private static MaintenanceSaveDTO maintenanceDTO(String targetNo, String content) {
        MaintenanceSaveDTO dto = new MaintenanceSaveDTO();
        dto.setTargetType(DeviceMaintenance.TARGET_VEHICLE);
        dto.setTargetNo(targetNo);
        dto.setContent(content);
        dto.setMaintDate(LocalDate.of(2026, 7, 30));
        return dto;
    }
}

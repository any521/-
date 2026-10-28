package com.dianfei.business.ops.controller;

import com.dianfei.business.ops.dto.LeaveAuditDTO;
import com.dianfei.business.ops.dto.MaintenanceSaveDTO;
import com.dianfei.business.ops.dto.ScheduleSaveDTO;
import com.dianfei.business.ops.entity.DeviceMaintenance;
import com.dianfei.business.ops.entity.LeaveRequest;
import com.dianfei.business.ops.entity.Schedule;
import com.dianfei.business.ops.service.DeviceMaintenanceService;
import com.dianfei.business.ops.service.LeaveService;
import com.dianfei.business.ops.service.ScheduleService;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 后台 · 排班运维（C4）：节假日排班、跨片区调配、请假审核、车辆设备运维登记
 */
@Tag(name = "后台-排班运维")
@RestController
@RequestMapping("/api/admin/ops")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminOpsController {

    private final ScheduleService scheduleService;
    private final LeaveService leaveService;
    private final DeviceMaintenanceService deviceMaintenanceService;

    // ---------- 排班 ----------

    @Operation(summary = "排班分页（片区/员工/日期区间过滤）")
    @GetMapping("/schedules")
    public Result<PageVO<Schedule>> schedules(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) Long areaId,
                                              @RequestParam(required = false) Long staffId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.success(scheduleService.pageAdmin(areaId, staffId, dateFrom, dateTo, pageNum, pageSize));
    }

    @Operation(summary = "新增排班（节假日/跨片区支援；同员工同日同班次防冲突，请假区间拦截）")
    @OperLog(value = "新增排班", module = "排班运维")
    @PostMapping("/schedules")
    public Result<Schedule> createSchedule(@Valid @RequestBody ScheduleSaveDTO dto) {
        return Result.success(scheduleService.create(dto));
    }

    @Operation(summary = "删除排班")
    @OperLog(value = "删除排班", module = "排班运维")
    @DeleteMapping("/schedules/{id}")
    public Result<Void> deleteSchedule(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    // ---------- 请假审核 ----------

    @Operation(summary = "请假申请分页（状态/员工过滤，待审核优先）")
    @GetMapping("/leaves")
    public Result<PageVO<LeaveRequest>> leaves(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Long staffId) {
        return Result.success(leaveService.pageAdmin(status, staffId, pageNum, pageSize));
    }

    @Operation(summary = "请假审核：通过后同事务移除区间内既有排班")
    @OperLog(value = "请假审核", module = "排班运维")
    @PutMapping("/leaves/{id}/audit")
    public Result<LeaveRequest> auditLeave(@PathVariable Long id, @Valid @RequestBody LeaveAuditDTO dto) {
        return Result.success(leaveService.audit(id, UserContext.getUserId(), dto));
    }

    // ---------- 车辆设备运维 ----------

    @Operation(summary = "运维记录分页（对象类型/编号过滤）")
    @GetMapping("/maintenances")
    public Result<PageVO<DeviceMaintenance>> maintenances(@RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                                          @RequestParam(required = false) Integer targetType,
                                                          @RequestParam(required = false) String targetNo) {
        return Result.success(deviceMaintenanceService.pageAdmin(targetType, targetNo, pageNum, pageSize));
    }

    @Operation(summary = "登记车辆/设备运维（保养/维修/年检，登记后留痕不可改）")
    @OperLog(value = "登记运维记录", module = "排班运维")
    @PostMapping("/maintenances")
    public Result<DeviceMaintenance> registerMaintenance(@Valid @RequestBody MaintenanceSaveDTO dto) {
        return Result.success(deviceMaintenanceService.register(UserContext.getUserId(), dto));
    }
}

package com.dianfei.business.ops.controller;

import com.dianfei.business.ops.dto.LeaveCreateDTO;
import com.dianfei.business.ops.entity.LeaveRequest;
import com.dianfei.business.ops.entity.Schedule;
import com.dianfei.business.ops.service.LeaveService;
import com.dianfei.business.ops.service.ScheduleService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工端 · 排班与请假（C4）：查看我的排班、提交请假申请
 */
@Tag(name = "员工端-排班请假")
@RestController
@RequestMapping("/api/staff/ops")
@RequiredArgsConstructor
public class StaffOpsController {

    private final ScheduleService scheduleService;
    private final LeaveService leaveService;
    private final StaffService staffService;

    @Operation(summary = "我的排班（日期区间）")
    @GetMapping("/schedules")
    public Result<List<Schedule>> mySchedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        Long staffId = staffService.requireOnDutyByUserId(UserContext.getUserId()).getId();
        return Result.success(scheduleService.listStaffSchedules(staffId, dateFrom, dateTo));
    }

    @Operation(summary = "提交请假申请（区间重叠的待审/已批单拦截）")
    @PostMapping("/leaves")
    public Result<LeaveRequest> submitLeave(@Valid @RequestBody LeaveCreateDTO dto) {
        return Result.success(leaveService.submit(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的请假申请")
    @GetMapping("/leaves")
    public Result<PageVO<LeaveRequest>> myLeaves(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                                 @RequestParam(required = false) Integer status) {
        return Result.success(leaveService.pageStaff(UserContext.getUserId(), status, pageNum, pageSize));
    }
}

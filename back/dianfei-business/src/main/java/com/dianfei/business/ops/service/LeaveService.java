package com.dianfei.business.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.ops.dto.LeaveAuditDTO;
import com.dianfei.business.ops.dto.LeaveCreateDTO;
import com.dianfei.business.ops.entity.LeaveRequest;
import com.dianfei.business.ops.entity.Schedule;
import com.dianfei.business.ops.mapper.LeaveRequestMapper;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工请假（C4）：员工端提交，后台审核；
 * 审核通过后请假区间内已有排班同事务自动移除（避免请假与排班冲突）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService extends ServiceImpl<LeaveRequestMapper, LeaveRequest> {

    private final StaffService staffService;
    private final MessageSender messageSender;

    /** 员工端：提交请假申请（按登录用户定位在岗员工，区间重叠的待审/已批单拦截） */
    @Transactional
    public LeaveRequest submit(Long userId, LeaveCreateDTO dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        boolean overlapped = lambdaQuery()
                .eq(LeaveRequest::getStaffId, staff.getId())
                .in(LeaveRequest::getStatus, LeaveRequest.STATUS_PENDING, LeaveRequest.STATUS_APPROVED)
                .le(LeaveRequest::getStartDate, dto.getEndDate())
                .ge(LeaveRequest::getEndDate, dto.getStartDate())
                .exists();
        if (overlapped) {
            throw new BusinessException("该区间已有待审核或已批准的请假申请");
        }
        LeaveRequest leave = new LeaveRequest();
        leave.setStaffId(staff.getId());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setReason(dto.getReason());
        leave.setStatus(LeaveRequest.STATUS_PENDING);
        save(leave);
        return leave;
    }

    /** 员工端：我的请假申请 */
    public PageVO<LeaveRequest> pageStaff(Long userId, Integer status, int pageNum, int pageSize) {
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStaffId, staff.getId())
                .eq(status != null, LeaveRequest::getStatus, status)
                .orderByDesc(LeaveRequest::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 后台：请假申请分页（状态/员工过滤，待审核优先） */
    public PageVO<LeaveRequest> pageAdmin(Integer status, Long staffId, int pageNum, int pageSize) {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<LeaveRequest>()
                .eq(status != null, LeaveRequest::getStatus, status)
                .eq(staffId != null, LeaveRequest::getStaffId, staffId)
                .orderByAsc(LeaveRequest::getStatus).orderByDesc(LeaveRequest::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 后台审核（仅待审核单）：通过后同事务移除请假区间内已有排班，失败整体回滚；
     * 结果站内信通知员工
     */
    @Transactional
    public LeaveRequest audit(Long leaveId, Long auditorId, LeaveAuditDTO dto) {
        LeaveRequest leave = getById(leaveId);
        if (leave == null) {
            throw new BusinessException("请假申请不存在");
        }
        if (leave.getStatus() != LeaveRequest.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "该请假申请已审核");
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        leave.setStatus(approved ? LeaveRequest.STATUS_APPROVED : LeaveRequest.STATUS_REJECTED);
        leave.setAuditBy(auditorId);
        leave.setAuditRemark(dto.getAuditRemark());
        leave.setAuditTime(LocalDateTime.now());
        updateById(leave);

        if (approved) {
            int removed = removeSchedulesInRange(leave);
            log.info("请假批准，移除区间内排班 {} 条：leaveId={}, staffId={}", removed, leaveId, leave.getStaffId());
        }
        BizStaff staff = staffService.getById(leave.getStaffId());
        if (staff != null) {
            messageSender.send(staff.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                    approved ? "请假已批准" : "请假被驳回",
                    "您 " + leave.getStartDate() + " 至 " + leave.getEndDate() + " 的请假申请"
                            + (approved ? "已批准" : "被驳回")
                            + (dto.getAuditRemark() == null ? "" : "：" + dto.getAuditRemark()));
        }
        return leave;
    }

    /** 指定日期是否落在该员工已批准的请假区间内（排班冲突校验用） */
    public boolean hasApprovedLeave(Long staffId, LocalDate date) {
        return lambdaQuery()
                .eq(LeaveRequest::getStaffId, staffId)
                .eq(LeaveRequest::getStatus, LeaveRequest.STATUS_APPROVED)
                .le(LeaveRequest::getStartDate, date)
                .ge(LeaveRequest::getEndDate, date)
                .exists();
    }

    /** 移除请假区间内的既有排班（直接走 ScheduleMapper 同库删除，避免与 ScheduleService 循环依赖） */
    private int removeSchedulesInRange(LeaveRequest leave) {
        List<Schedule> schedules = com.baomidou.mybatisplus.extension.toolkit.Db.lambdaQuery(Schedule.class)
                .eq(Schedule::getStaffId, leave.getStaffId())
                .ge(Schedule::getWorkDate, leave.getStartDate())
                .le(Schedule::getWorkDate, leave.getEndDate())
                .list();
        if (!schedules.isEmpty()) {
            com.baomidou.mybatisplus.extension.toolkit.Db.removeByIds(
                    schedules.stream().map(Schedule::getId).toList(), Schedule.class);
        }
        return schedules.size();
    }
}

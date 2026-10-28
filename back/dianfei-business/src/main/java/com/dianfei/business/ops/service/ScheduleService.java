package com.dianfei.business.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.ops.dto.ScheduleSaveDTO;
import com.dianfei.business.ops.entity.Schedule;
import com.dianfei.business.ops.mapper.ScheduleMapper;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工排班（C4）：节假日排班、跨片区人力调配；
 * 员工+日期+班次防冲突，已批准请假区间内禁止排班
 */
@Service
@RequiredArgsConstructor
public class ScheduleService extends ServiceImpl<ScheduleMapper, Schedule> {

    private final StaffService staffService;
    private final AreaService areaService;
    private final LeaveService leaveService;

    /** 后台：排班分页（片区/员工/日期区间过滤） */
    public PageVO<Schedule> pageAdmin(Long areaId, Long staffId, LocalDate dateFrom, LocalDate dateTo,
                                      int pageNum, int pageSize) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(areaId != null, Schedule::getAreaId, areaId)
                .eq(staffId != null, Schedule::getStaffId, staffId)
                .ge(dateFrom != null, Schedule::getWorkDate, dateFrom)
                .le(dateTo != null, Schedule::getWorkDate, dateTo)
                .orderByAsc(Schedule::getWorkDate).orderByAsc(Schedule::getShiftType);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 新增排班：员工须在岗；同员工+日期+班次唯一；
     * 跨片区支援须指定启用的目标片区；已批准请假区间内不可排班
     */
    @Transactional
    public Schedule create(ScheduleSaveDTO dto) {
        if (dto.getShiftType() != Schedule.SHIFT_DAY && dto.getShiftType() != Schedule.SHIFT_NIGHT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "班次不合法");
        }
        if (dto.getBizType() != Schedule.BIZ_NORMAL && dto.getBizType() != Schedule.BIZ_HOLIDAY
                && dto.getBizType() != Schedule.BIZ_SUPPORT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排班类型不合法");
        }
        BizStaff staff = staffService.getById(dto.getStaffId());
        if (staff == null || staff.getAuditStatus() != BizStaff.AUDIT_APPROVED || staff.getStatus() != 1) {
            throw new BusinessException("员工不存在或不在岗");
        }
        if (dto.getBizType() == Schedule.BIZ_SUPPORT) {
            if (dto.getSupportAreaId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "跨片区支援必须指定目标片区");
            }
            areaService.requireEnabled(dto.getSupportAreaId());
        }
        boolean conflict = lambdaQuery()
                .eq(Schedule::getStaffId, dto.getStaffId())
                .eq(Schedule::getWorkDate, dto.getWorkDate())
                .eq(Schedule::getShiftType, dto.getShiftType())
                .exists();
        if (conflict) {
            throw new BusinessException("该员工当日该班次已有排班");
        }
        if (leaveService.hasApprovedLeave(dto.getStaffId(), dto.getWorkDate())) {
            throw new BusinessException("该员工请假区间内不可排班");
        }
        Schedule schedule = new Schedule();
        schedule.setStaffId(dto.getStaffId());
        schedule.setAreaId(staff.getAreaId());
        schedule.setWorkDate(dto.getWorkDate());
        schedule.setShiftType(dto.getShiftType());
        schedule.setBizType(dto.getBizType());
        schedule.setSupportAreaId(dto.getBizType() == Schedule.BIZ_SUPPORT ? dto.getSupportAreaId() : null);
        schedule.setRemark(dto.getRemark());
        save(schedule);
        return schedule;
    }

    /** 删除排班 */
    @Transactional
    public void delete(Long scheduleId) {
        if (getById(scheduleId) == null) {
            throw new BusinessException("排班不存在");
        }
        removeById(scheduleId);
    }

    /** 员工端：我的排班（日期区间） */
    public List<Schedule> listStaffSchedules(Long staffId, LocalDate dateFrom, LocalDate dateTo) {
        return lambdaQuery()
                .eq(Schedule::getStaffId, staffId)
                .ge(dateFrom != null, Schedule::getWorkDate, dateFrom)
                .le(dateTo != null, Schedule::getWorkDate, dateTo)
                .orderByAsc(Schedule::getWorkDate).orderByAsc(Schedule::getShiftType)
                .list();
    }
}

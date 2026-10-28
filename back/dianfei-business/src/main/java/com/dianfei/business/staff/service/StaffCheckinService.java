package com.dianfei.business.staff.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.staff.dto.CheckinDTO;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.entity.BizStaffCheckin;
import com.dianfei.business.staff.mapper.BizStaffCheckinMapper;
import com.dianfei.business.staff.vo.CheckinTodayVO;
import com.dianfei.business.staff.vo.CheckinVO;
import com.dianfei.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 员工签到（G9）：上班/下班两段打卡，每人每日一条（uk_checkin_staff_date）；
 * 时间格式统一 yyyy-MM-dd HH:mm:ss（项目全局约定）。
 */
@Service
@RequiredArgsConstructor
public class StaffCheckinService extends ServiceImpl<BizStaffCheckinMapper, BizStaffCheckin> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StaffService staffService;

    /** 打卡：'ON' 上班 / 'OFF' 下班，返回本次打卡时间 */
    @Transactional
    public CheckinVO checkin(Long userId, CheckinDTO dto) {
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        BizStaffCheckin row = findByStaffAndDate(staff.getId(), today);
        String type = dto.getType();
        if ("ON".equals(type)) {
            if (row == null) {
                row = new BizStaffCheckin();
                row.setStaffId(staff.getId());
                row.setCheckDate(today);
                row.setOnTime(now);
                row.setOnLocation(dto.getLocation());
                save(row);
            } else if (row.getOnTime() != null) {
                throw new BusinessException("今日已打上班卡");
            } else {
                row.setOnTime(now);
                row.setOnLocation(dto.getLocation());
                updateById(row);
            }
        } else if ("OFF".equals(type)) {
            if (row == null || row.getOnTime() == null) {
                throw new BusinessException("请先打上班卡");
            }
            if (row.getOffTime() != null) {
                throw new BusinessException("今日已打下班卡");
            }
            row.setOffTime(now);
            row.setOffLocation(dto.getLocation());
            updateById(row);
        } else {
            throw new BusinessException("打卡类型不合法");
        }
        return new CheckinVO(TIME_FMT.format(now));
    }

    /** 今日签到状态：未打为 ""（勿返 null，前端契约） */
    public CheckinTodayVO today(Long userId) {
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        BizStaffCheckin row = findByStaffAndDate(staff.getId(), LocalDate.now());
        return new CheckinTodayVO(
                row == null || row.getOnTime() == null ? "" : TIME_FMT.format(row.getOnTime()),
                row == null || row.getOffTime() == null ? "" : TIME_FMT.format(row.getOffTime()));
    }

    private BizStaffCheckin findByStaffAndDate(Long staffId, LocalDate date) {
        return lambdaQuery()
                .eq(BizStaffCheckin::getStaffId, staffId)
                .eq(BizStaffCheckin::getCheckDate, date)
                .one();
    }
}

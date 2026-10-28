package com.dianfei.business.staff.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.staff.dto.StaffApplyDTO;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.mapper.BizStaffMapper;
import com.dianfei.business.staff.vo.StaffApplyVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作人员自助申请（G1，onsite01）：实名已通过 → 工种限回收员/清运工/技术员 →
 * 同用户查重（auditStatus∈{0,1} 拦截）→ 无记录 INSERT / 已驳回复用行 UPDATE
 * （biz_staff.uk_staff_user 唯一键约束，重新申请不新增行）
 */
@Service
@RequiredArgsConstructor
public class StaffApplyService {

    /** 自助申请开放工种：1回收员 2清运工（环卫员） 3维修技术员 */
    private static final int TYPE_RECYCLER = 1;
    private static final int TYPE_SANITATION = 2;
    private static final int TYPE_TECHNICIAN = 3;

    private final BizStaffMapper bizStaffMapper;
    private final SysUserMapper sysUserMapper;
    private final AreaService areaService;

    @Transactional(rollbackFor = Exception.class)
    public void apply(Long userId, StaffApplyDTO dto) {
        // ① 实名已通过（V17：sys_user.real_name_status 0未认证 1待审 2已通过 3已驳回）
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getRealNameStatus() == null || user.getRealNameStatus() != 2) {
            throw new BusinessException("申请工作人员需先完成实名认证");
        }
        // ② 工种限回收员/清运工（环卫员）/维修技术员
        if (dto.getStaffType() == null
                || (dto.getStaffType() != TYPE_RECYCLER
                && dto.getStaffType() != TYPE_SANITATION
                && dto.getStaffType() != TYPE_TECHNICIAN)) {
            throw new BusinessException("仅支持申请回收员、清运工或维修技术员");
        }
        // ③ 片区存在性（可空=待审核通过后由平台分配）
        if (dto.getAreaId() != null) {
            areaService.requireEnabled(dto.getAreaId());
        }
        // ④ 同用户查重：最新一条申请在审核中/已通过 → 拦截；已驳回 → 复用行重新申请
        BizStaff latest = latestByUserId(userId);
        if (latest != null
                && (latest.getAuditStatus() == BizStaff.AUDIT_PENDING
                || latest.getAuditStatus() == BizStaff.AUDIT_APPROVED)) {
            throw new BusinessException("已有进行中的申请，请等待审核结果");
        }
        if (latest == null) {
            BizStaff staff = new BizStaff();
            staff.setUserId(userId);
            staff.setStaffName(dto.getStaffName().trim());
            staff.setPhone(dto.getPhone().trim());
            staff.setStaffType(dto.getStaffType());
            staff.setAreaId(dto.getAreaId());
            staff.setAuditStatus(BizStaff.AUDIT_PENDING);
            staff.setStatus(1);
            staff.setApplyScene(1);
            staff.setApplyReason(trimToNull(dto.getReason()));
            bizStaffMapper.insert(staff);
        } else {
            latest.setStaffName(dto.getStaffName().trim());
            latest.setPhone(dto.getPhone().trim());
            latest.setStaffType(dto.getStaffType());
            latest.setAreaId(dto.getAreaId());
            latest.setAuditStatus(BizStaff.AUDIT_PENDING);
            latest.setStatus(1);
            latest.setApplyScene(1);
            latest.setApplyReason(trimToNull(dto.getReason()));
            bizStaffMapper.updateById(latest);
        }
    }

    public StaffApplyVO status(Long userId) {
        BizStaff latest = latestByUserId(userId);
        StaffApplyVO vo = new StaffApplyVO();
        if (latest == null) {
            vo.setApplied(false);
            return vo;
        }
        vo.setApplied(true);
        vo.setAuditStatus(latest.getAuditStatus());
        vo.setStaffType(latest.getStaffType());
        vo.setStaffId(latest.getId());
        // 驳回原因优先（V58），旧数据无审核备注时退回申请理由展示，避免空白
        vo.setReason(latest.getAuditRemark() != null ? latest.getAuditRemark() : latest.getApplyReason());
        return vo;
    }

    /** 用户最新一条员工档案（deleted=0 由逻辑删除自动过滤；无则 null） */
    private BizStaff latestByUserId(Long userId) {
        return bizStaffMapper.selectList(new LambdaQueryWrapper<BizStaff>()
                        .eq(BizStaff::getUserId, userId)
                        .orderByDesc(BizStaff::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

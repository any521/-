package com.dianfei.business.staff.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.staff.dto.StaffAuditDTO;
import com.dianfei.business.staff.dto.StaffCreateDTO;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.mapper.BizStaffMapper;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 员工管理（B1）：新增+审核上岗、禁用/启用、片区调整
 */
@Service
@RequiredArgsConstructor
public class StaffService extends ServiceImpl<BizStaffMapper, BizStaff> {

    private final AreaService areaService;
    private final SysUserMapper sysUserMapper;

    /** 同步 sys_user.area_id = 员工负责片区（登录 token 数据权限依据与 biz_staff 保持一致；换片区/审核通过即同步） */
    private void syncUserAreaId(Long userId, Long areaId) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && !java.util.Objects.equals(user.getAreaId(), areaId)) {
            user.setAreaId(areaId);
            sysUserMapper.updateById(user);
        }
    }

    /** 同步 sys_user.role = 员工工种（员工端角色视图依据；档案工种变更审核通过即同步，防前端按旧角色展示模块却操作被拒） */
    private void syncUserRole(Long userId, Integer staffType) {
        if (userId == null) {
            return;
        }
        String role = staffTypeToRole(staffType);
        if (role == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && !role.equals(user.getRole())) {
            user.setRole(role);
            sysUserMapper.updateById(user);
        }
    }

    /** 员工工种 → 系统角色编码（1回收员 2环卫员 3技术员） */
    private static String staffTypeToRole(Integer staffType) {
        return switch (staffType == null ? -1 : staffType) {
            case 1 -> "RECYCLER";
            case 2 -> "SANITATION";
            case 3 -> "TECHNICIAN";
            default -> null;
        };
    }

    public PageVO<BizStaff> page(int pageNum, int pageSize, Integer staffType, Long areaId, Integer auditStatus) {
        LambdaQueryWrapper<BizStaff> wrapper = new LambdaQueryWrapper<BizStaff>()
                .eq(staffType != null, BizStaff::getStaffType, staffType)
                .eq(areaId != null, BizStaff::getAreaId, areaId)
                .eq(auditStatus != null, BizStaff::getAuditStatus, auditStatus)
                .orderByDesc(BizStaff::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Transactional
    public BizStaff create(StaffCreateDTO dto) {
        if (StaffTypeEnum.of(dto.getStaffType()) == null) {
            throw new BusinessException("员工类型不合法");
        }
        areaService.requireEnabled(dto.getAreaId());
        if (lambdaQuery().eq(BizStaff::getUserId, dto.getUserId()).exists()) {
            throw new BusinessException("该用户已登记为员工");
        }
        BizStaff staff = new BizStaff();
        staff.setUserId(dto.getUserId());
        staff.setStaffName(dto.getStaffName());
        staff.setPhone(dto.getPhone());
        staff.setStaffType(dto.getStaffType());
        staff.setAreaId(dto.getAreaId());
        staff.setAuditStatus(BizStaff.AUDIT_PENDING);
        staff.setStatus(1);
        save(staff);
        return staff;
    }

    @Transactional
    public void audit(Long staffId, StaffAuditDTO dto) {
        BizStaff staff = requireStaff(staffId);
        if (staff.getAuditStatus() != BizStaff.AUDIT_PENDING) {
            throw new BusinessException("仅待审核员工可审核");
        }
        staff.setAuditStatus(Boolean.TRUE.equals(dto.getApproved())
                ? BizStaff.AUDIT_APPROVED : BizStaff.AUDIT_REJECTED);
        // 驳回原因落库（V58：audit_remark 独立列，用户端申请状态接口据此展示驳回信息）；
        // 通过时清空历史驳回原因，避免重新申请通过后残留误导
        staff.setAuditRemark(Boolean.TRUE.equals(dto.getApproved())
                ? null : trimToNull(dto.getRemark()));
        updateById(staff);
        // 审核通过即上岗：同步 sys_user.area_id（员工片区数据权限依据）+ role（员工端差异化视图依据）
        if (Boolean.TRUE.equals(dto.getApproved())) {
            syncUserAreaId(staff.getUserId(), staff.getAreaId());
            syncUserRole(staff.getUserId(), staff.getStaffType());
        }
    }

    @Transactional
    public void changeStatus(Long staffId, boolean enable) {
        BizStaff staff = requireStaff(staffId);
        staff.setStatus(enable ? 1 : 0);
        updateById(staff);
    }

    @Transactional
    public void transferArea(Long staffId, Long areaId) {
        BizStaff staff = requireStaff(staffId);
        areaService.requireEnabled(areaId);
        staff.setAreaId(areaId);
        updateById(staff);
        syncUserAreaId(staff.getUserId(), areaId);
    }

    /** 员工自助换片区：按登录用户定位在岗员工，校验目标片区启用后更新（接单/列表即时按新片区生效） */
    @Transactional
    public void changeMyArea(Long userId, Long areaId) {
        BizStaff staff = requireOnDutyByUserId(userId);
        areaService.requireEnabled(areaId);
        staff.setAreaId(areaId);
        updateById(staff);
        syncUserAreaId(userId, areaId);
    }

    /** 片区内在岗（审核通过+启用）的指定类型员工 */
    public List<BizStaff> listOnDuty(Long areaId, StaffTypeEnum type) {
        return lambdaQuery()
                .eq(BizStaff::getAreaId, areaId)
                .eq(BizStaff::getStaffType, type.getCode())
                .eq(BizStaff::getAuditStatus, BizStaff.AUDIT_APPROVED)
                .eq(BizStaff::getStatus, 1)
                .list();
    }

    /** 按登录用户 ID 取在岗员工（员工端接口鉴权用） */
    public BizStaff requireOnDutyByUserId(Long userId) {
        BizStaff staff = lambdaQuery().eq(BizStaff::getUserId, userId).one();
        if (staff == null || staff.getAuditStatus() != BizStaff.AUDIT_APPROVED || staff.getStatus() != 1) {
            throw new BusinessException("当前用户不是在岗员工");
        }
        return staff;
    }

    private BizStaff requireStaff(Long staffId) {
        BizStaff staff = getById(staffId);
        if (staff == null) {
            throw new BusinessException("员工不存在");
        }
        return staff;
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

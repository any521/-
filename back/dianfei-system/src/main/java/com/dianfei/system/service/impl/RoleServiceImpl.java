package com.dianfei.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.TombstoneUtil;
import com.dianfei.system.dto.AssignMenuDTO;
import com.dianfei.system.dto.RoleDTO;
import com.dianfei.system.dto.RoleQueryDTO;
import com.dianfei.system.entity.SysRole;
import com.dianfei.system.entity.SysRoleMenu;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysRoleMapper;
import com.dianfei.system.mapper.SysRoleMenuMapper;
import com.dianfei.system.mapper.SysUserMapper;
import com.dianfei.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色服务实现（权限模型：sys_user.role = sys_role.role_code → sys_role_menu → sys_menu）
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public PageVO<SysRole> page(RoleQueryDTO query) {
        Page<SysRole> page = sysRoleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<SysRole>()
                        .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                                .like(SysRole::getRoleName, query.getKeyword())
                                .or().like(SysRole::getRoleCode, query.getKeyword()))
                        .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                        .orderByAsc(SysRole::getId));
        return PageVO.of(page);
    }

    @Override
    public void add(RoleDTO dto) {
        checkCodeUnique(dto.getRoleCode(), null);
        SysRole role = BeanUtil.copyProperties(dto, SysRole.class);
        role.setId(null);
        role.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        sysRoleMapper.insert(role);
    }

    @Override
    public void edit(RoleDTO dto) {
        if (dto.getId() == null || sysRoleMapper.selectById(dto.getId()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色不存在");
        }
        checkCodeUnique(dto.getRoleCode(), dto.getId());
        sysRoleMapper.updateById(BeanUtil.copyProperties(dto, SysRole.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            return;
        }
        Long inUse = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, role.getRoleCode()));
        if (inUse != null && inUse > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色已被用户使用，不允许删除");
        }
        // 墓碑改写唯一键后再逻辑删除，释放 uk_role_code 供同码重建（列宽 32）
        role.setRoleCode(TombstoneUtil.mark(role.getRoleCode(), role.getId(), 32));
        sysRoleMapper.updateById(role);
        sysRoleMapper.deleteById(id);
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(AssignMenuDTO dto) {
        if (sysRoleMapper.selectById(dto.getRoleId()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色不存在");
        }
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, dto.getRoleId()));
        dto.getMenuIds().stream().distinct().forEach(menuId -> {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(dto.getRoleId());
            roleMenu.setMenuId(menuId);
            sysRoleMenuMapper.insert(roleMenu);
        });
    }

    @Override
    public List<Long> menuIdsOfRole(Long roleId) {
        return sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId)).stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
    }

    private void checkCodeUnique(String roleCode, Long excludeId) {
        Long count = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色编码已存在");
        }
    }
}

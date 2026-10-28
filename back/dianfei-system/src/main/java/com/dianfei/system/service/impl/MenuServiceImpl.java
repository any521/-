package com.dianfei.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.dto.MenuDTO;
import com.dianfei.system.entity.SysMenu;
import com.dianfei.system.entity.SysRole;
import com.dianfei.system.entity.SysRoleMenu;
import com.dianfei.system.mapper.SysMenuMapper;
import com.dianfei.system.mapper.SysRoleMapper;
import com.dianfei.system.mapper.SysRoleMenuMapper;
import com.dianfei.system.service.MenuService;
import com.dianfei.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单服务实现：树构建 + 角色权限码/动态路由查询（权限模型：role_code → sys_role_menu → sys_menu.perm）
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 按钮类型（perm 权限码所在层级） */
    private static final int TYPE_BUTTON = 3;

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<MenuTreeVO> listTree() {
        List<SysMenu> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, false)
                .orderByAsc(SysMenu::getSort));
        return buildTree(menus);
    }

    @Override
    public void add(MenuDTO dto) {
        checkParent(dto.getParentId(), null);
        SysMenu menu = BeanUtil.copyProperties(dto, SysMenu.class);
        menu.setId(null);
        sysMenuMapper.insert(menu);
    }

    @Override
    public void edit(MenuDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "菜单 ID 不能为空");
        }
        // 检查菜单是否存在且未被删除
        SysMenu existing = sysMenuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getId, dto.getId())
                        .eq(SysMenu::getDeleted, false));
        if (existing == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "菜单不存在或已被删除");
        }
        if (dto.getId().equals(dto.getParentId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "父菜单不能是自身");
        }
        checkParent(dto.getParentId(), dto.getId());
        sysMenuMapper.updateById(BeanUtil.copyProperties(dto, SysMenu.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        // 先检查是否有子菜单（未删除的）
        Long children = sysMenuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getParentId, id)
                        .eq(SysMenu::getDeleted, false));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "存在子菜单，不允许删除");
        }
        // 物理删除：直接删除数据库记录（不走 MyBatis-Plus 的逻辑删除）
        sysMenuMapper.deleteById(id);
        // 同步清理角色 - 菜单关联
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getMenuId, id));
    }

    @Override
    public Set<String> permsByRoleCode(String roleCode) {
        return menusOfRole(roleCode).stream()
                .map(SysMenu::getPerm)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    @Override
    public List<MenuTreeVO> treeByRoleCode(String roleCode) {
        List<SysMenu> menus = menusOfRole(roleCode).stream()
                .filter(menu -> menu.getMenuType() != TYPE_BUTTON)
                .toList();
        return buildTree(menus);
    }

    /** 角色可见的启用菜单（目录+菜单+按钮） */
    private List<SysMenu> menusOfRole(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getStatus, 1)
                .last("LIMIT 1"));
        if (role == null) {
            return Collections.emptyList();
        }
        List<Long> menuIds = sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, role.getId())).stream()
                .map(SysRoleMenu::getMenuId)
                .toList();
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSort));
    }

    private void checkParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        // 检查父菜单是否存在且未被删除
        SysMenu parent = sysMenuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getId, parentId)
                        .eq(SysMenu::getDeleted, false));
        if (parent == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "父菜单不存在或已被删除");
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "父菜单不能是自身");
        }
    }

    /** 平铺列表 → 树（parentId=0 为根，孤儿节点挂根下防丢失） */
    private List<MenuTreeVO> buildTree(List<SysMenu> menus) {
        Map<Long, MenuTreeVO> nodeMap = menus.stream()
                .map(menu -> BeanUtil.copyProperties(menu, MenuTreeVO.class))
                .collect(Collectors.toMap(MenuTreeVO::getId, Function.identity(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        List<MenuTreeVO> roots = new java.util.ArrayList<>();
        nodeMap.values().forEach(node -> {
            MenuTreeVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        });
        roots.sort(Comparator.comparing(MenuTreeVO::getSort,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return roots;
    }
}

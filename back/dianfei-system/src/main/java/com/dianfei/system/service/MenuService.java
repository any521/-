package com.dianfei.system.service;

import com.dianfei.system.dto.MenuDTO;
import com.dianfei.system.vo.MenuTreeVO;

import java.util.List;
import java.util.Set;

/**
 * 菜单服务
 */
public interface MenuService {

    /** 全量菜单树（菜单管理页） */
    List<MenuTreeVO> listTree();

    void add(MenuDTO dto);

    void edit(MenuDTO dto);

    void remove(Long id);

    /** 角色可见的按钮权限码集合 */
    Set<String> permsByRoleCode(String roleCode);

    /** 角色可见的菜单树（目录+菜单，供动态路由） */
    List<MenuTreeVO> treeByRoleCode(String roleCode);
}

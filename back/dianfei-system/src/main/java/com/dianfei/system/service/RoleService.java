package com.dianfei.system.service;

import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.AssignMenuDTO;
import com.dianfei.system.dto.RoleDTO;
import com.dianfei.system.dto.RoleQueryDTO;
import com.dianfei.system.entity.SysRole;

import java.util.List;

/**
 * 角色服务
 */
public interface RoleService {

    PageVO<SysRole> page(RoleQueryDTO query);

    void add(RoleDTO dto);

    void edit(RoleDTO dto);

    void remove(Long id);

    /** 全量覆盖式分配菜单 */
    void assignMenus(AssignMenuDTO dto);

    /** 角色已分配的菜单 id 集合（分配菜单回显） */
    List<Long> menuIdsOfRole(Long roleId);
}

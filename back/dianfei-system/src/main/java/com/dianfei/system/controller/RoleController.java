package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.system.dto.AssignMenuDTO;
import com.dianfei.system.dto.RoleDTO;
import com.dianfei.system.dto.RoleQueryDTO;
import com.dianfei.system.entity.SysRole;
import com.dianfei.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理（管理端，A4）
 */
@Tag(name = "系统管理-角色")
@RestController
@RequestMapping("/api/system/role")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "角色分页列表")
    @GetMapping("/page")
    public Result<PageVO<SysRole>> page(@Valid RoleQueryDTO query) {
        return Result.success(roleService.page(query));
    }

    @Operation(summary = "新增角色")
    @OperLog(value = "新增角色", module = "系统管理")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody RoleDTO dto) {
        roleService.add(dto);
        return Result.success();
    }

    @Operation(summary = "编辑角色")
    @OperLog(value = "编辑角色", module = "系统管理")
    @PutMapping
    public Result<Void> edit(@Valid @RequestBody RoleDTO dto) {
        roleService.edit(dto);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @OperLog(value = "删除角色", module = "系统管理")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        roleService.remove(id);
        return Result.success();
    }

    @Operation(summary = "角色分配菜单（全量覆盖）")
    @OperLog(value = "分配菜单", module = "系统管理")
    @PostMapping("/assign-menu")
    public Result<Void> assignMenus(@Valid @RequestBody AssignMenuDTO dto) {
        roleService.assignMenus(dto);
        return Result.success();
    }

    @Operation(summary = "角色已分配的菜单 id（回显）")
    @GetMapping("/{id}/menu-ids")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.success(roleService.menuIdsOfRole(id));
    }
}

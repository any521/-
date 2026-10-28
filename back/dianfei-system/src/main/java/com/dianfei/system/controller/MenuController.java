package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import com.dianfei.system.dto.MenuDTO;
import com.dianfei.system.service.MenuService;
import com.dianfei.system.vo.MenuTreeVO;
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
 * 菜单管理（管理端，A4）
 */
@Tag(name = "系统管理-菜单")
@RestController
@RequestMapping("/api/system/menu")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "全量菜单树")
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> tree() {
        return Result.success(menuService.listTree());
    }

    @Operation(summary = "新增菜单")
    @OperLog(value = "新增菜单", module = "系统管理")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody MenuDTO dto) {
        menuService.add(dto);
        return Result.success();
    }

    @Operation(summary = "编辑菜单")
    @OperLog(value = "编辑菜单", module = "系统管理")
    @PutMapping
    public Result<Void> edit(@Valid @RequestBody MenuDTO dto) {
        menuService.edit(dto);
        return Result.success();
    }

    @Operation(summary = "删除菜单（有子菜单时拒绝）")
    @OperLog(value = "删除菜单", module = "系统管理")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        menuService.remove(id);
        return Result.success();
    }
}

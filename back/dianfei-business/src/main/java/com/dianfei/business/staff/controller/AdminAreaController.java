package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.dto.AreaSaveDTO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · 片区管理（B1）
 */
@Tag(name = "后台-片区管理")
@RestController
@RequestMapping("/api/admin/areas")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminAreaController {

    private final AreaService areaService;

    @Operation(summary = "片区分页查询")
    @GetMapping
    public Result<PageVO<BizArea>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String keyword) {
        return Result.success(areaService.page(pageNum, pageSize, keyword));
    }

    @Operation(summary = "片区详情")
    @GetMapping("/{id}")
    public Result<BizArea> detail(@PathVariable Long id) {
        return Result.success(areaService.getById(id));
    }

    @Operation(summary = "新增片区")
    @OperLog(value = "新增片区", module = "片区管理")
    @PostMapping
    public Result<BizArea> create(@Valid @RequestBody AreaSaveDTO dto) {
        dto.setId(null);
        return Result.success(areaService.saveArea(dto));
    }

    @Operation(summary = "修改片区")
    @OperLog(value = "修改片区", module = "片区管理")
    @PutMapping("/{id}")
    public Result<BizArea> update(@PathVariable Long id, @Valid @RequestBody AreaSaveDTO dto) {
        dto.setId(id);
        return Result.success(areaService.saveArea(dto));
    }

    @Operation(summary = "删除片区（逻辑删除）")
    @OperLog(value = "删除片区", module = "片区管理")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        areaService.removeById(id);
        return Result.success();
    }
}

package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.system.dto.DictDataDTO;
import com.dianfei.system.dto.DictTypeDTO;
import com.dianfei.system.entity.SysDictData;
import com.dianfei.system.entity.SysDictType;
import com.dianfei.system.service.DictService;
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

import java.util.List;

/**
 * 字典管理（A4）：类型/数据 CRUD 为管理端；按类型取数据为三端通用（走 Redis 缓存）
 */
@Tag(name = "系统管理-字典")
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "按类型取启用字典数据（三端通用，走缓存）")
    @GetMapping("/data/{dictType}")
    public Result<List<SysDictData>> dataByType(@PathVariable String dictType) {
        return Result.success(dictService.dataByType(dictType));
    }

    // ---------- 类型（管理端） ----------

    @Operation(summary = "字典类型分页列表")
    @RequiresRole("ADMIN")
    @GetMapping("/type/page")
    public Result<PageVO<SysDictType>> pageType(@Valid PageDTO page,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(dictService.pageType(page, keyword));
    }

    @Operation(summary = "新增字典类型")
    @OperLog(value = "新增字典类型", module = "系统管理")
    @RequiresRole("ADMIN")
    @PostMapping("/type")
    public Result<Void> addType(@Valid @RequestBody DictTypeDTO dto) {
        dictService.addType(dto);
        return Result.success();
    }

    @Operation(summary = "编辑字典类型")
    @OperLog(value = "编辑字典类型", module = "系统管理")
    @RequiresRole("ADMIN")
    @PutMapping("/type")
    public Result<Void> editType(@Valid @RequestBody DictTypeDTO dto) {
        dictService.editType(dto);
        return Result.success();
    }

    @Operation(summary = "删除字典类型（有数据时拒绝）")
    @OperLog(value = "删除字典类型", module = "系统管理")
    @RequiresRole("ADMIN")
    @DeleteMapping("/type/{id}")
    public Result<Void> removeType(@PathVariable Long id) {
        dictService.removeType(id);
        return Result.success();
    }

    // ---------- 数据（管理端） ----------

    @Operation(summary = "字典数据全量列表（含停用，管理端）")
    @RequiresRole("ADMIN")
    @GetMapping("/data")
    public Result<List<SysDictData>> listData(@RequestParam(required = false) String dictType) {
        return Result.success(dictService.listData(dictType));
    }

    @Operation(summary = "新增字典数据")
    @OperLog(value = "新增字典数据", module = "系统管理")
    @RequiresRole("ADMIN")
    @PostMapping("/data")
    public Result<Void> addData(@Valid @RequestBody DictDataDTO dto) {
        dictService.addData(dto);
        return Result.success();
    }

    @Operation(summary = "编辑字典数据")
    @OperLog(value = "编辑字典数据", module = "系统管理")
    @RequiresRole("ADMIN")
    @PutMapping("/data")
    public Result<Void> editData(@Valid @RequestBody DictDataDTO dto) {
        dictService.editData(dto);
        return Result.success();
    }

    @Operation(summary = "删除字典数据")
    @OperLog(value = "删除字典数据", module = "系统管理")
    @RequiresRole("ADMIN")
    @DeleteMapping("/data/id/{id}")
    public Result<Void> removeData(@PathVariable Long id) {
        dictService.removeData(id);
        return Result.success();
    }
}

package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.system.dto.ConfigDTO;
import com.dianfei.system.entity.SysConfig;
import com.dianfei.system.service.ConfigService;
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
 * 参数配置（管理端，A4）：热更新，改库即刷缓存，业务不重启生效
 */
@Tag(name = "系统管理-参数配置")
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "参数分页列表")
    @RequiresRole("ADMIN")
    @GetMapping("/page")
    public Result<PageVO<SysConfig>> page(@Valid PageDTO page,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(configService.page(page, keyword));
    }

    @Operation(summary = "按键读参数值（三端通用，走缓存）")
    @GetMapping("/value/{configKey}")
    public Result<String> value(@PathVariable String configKey) {
        return Result.success(configService.getValue(configKey));
    }

    @Operation(summary = "新增参数")
    @OperLog(value = "新增参数", module = "系统管理")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody ConfigDTO dto) {
        configService.add(dto);
        return Result.success();
    }

    @Operation(summary = "编辑参数（热更新即时生效）")
    @OperLog(value = "编辑参数", module = "系统管理")
    @RequiresRole("ADMIN")
    @PutMapping
    public Result<Void> edit(@Valid @RequestBody ConfigDTO dto) {
        configService.edit(dto);
        return Result.success();
    }

    @Operation(summary = "删除参数")
    @OperLog(value = "删除参数", module = "系统管理")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        configService.remove(id);
        return Result.success();
    }
}

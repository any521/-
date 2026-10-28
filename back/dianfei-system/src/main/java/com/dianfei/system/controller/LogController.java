package com.dianfei.system.controller;

import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.system.dto.LoginLogQueryDTO;
import com.dianfei.system.dto.OperLogQueryDTO;
import com.dianfei.system.entity.SysLoginLog;
import com.dianfei.system.entity.SysOperLog;
import com.dianfei.system.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查询（A4，管理端）：操作日志（@OperLog 切面落库）与登录日志
 */
@Tag(name = "系统管理-日志")
@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(summary = "操作日志分页列表")
    @RequiresRole("ADMIN")
    @GetMapping("/oper/page")
    public Result<PageVO<SysOperLog>> pageOperLog(@Valid OperLogQueryDTO query) {
        return Result.success(logService.pageOperLog(query));
    }

    @Operation(summary = "登录日志分页列表")
    @RequiresRole("ADMIN")
    @GetMapping("/login/page")
    public Result<PageVO<SysLoginLog>> pageLoginLog(@Valid LoginLogQueryDTO query) {
        return Result.success(logService.pageLoginLog(query));
    }
}

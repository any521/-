package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.system.dto.AdminUserSaveDTO;
import com.dianfei.system.dto.RealNameAuditDTO;
import com.dianfei.system.service.SysUserAdminService;
import com.dianfei.system.vo.SysUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
 * 系统用户管理（管理端，G1 补齐）：分页 / 新增编辑（id 判语义）/ 逻辑删除
 */
@Tag(name = "系统管理 - 用户")
@RestController
@RequestMapping("/api/system/users")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserAdminService sysUserAdminService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "用户分页（keyword 匹配用户名/昵称/手机号，status 精确；realNameStatus 实名状态 0-3，待审=1）")
    @GetMapping
    public Result<PageVO<SysUserVO>> page(@Valid PageDTO page,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer realNameStatus) {
        return Result.success(sysUserAdminService.page(page, keyword, status, realNameStatus));
    }

    @Operation(summary = "新增/编辑用户（id 空=新增，非空=编辑；新增返回一次性初始密码）")
    @OperLog(value = "保存用户", module = "系统管理")
    @PostMapping
    public Result<String> save(@Valid @RequestBody AdminUserSaveDTO dto) {
        return Result.success(sysUserAdminService.save(dto));
    }

    @Operation(summary = "删除用户（逻辑删除；禁止删除当前登录用户）")
    @OperLog(value = "删除用户", module = "系统管理")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        sysUserAdminService.remove(id);
        return Result.success();
    }

    @Operation(summary = "实名审核（pass=true→通过，false→驳回写备注；仅待审状态可审）")
    @OperLog(value = "实名审核", module = "系统管理")
    @PutMapping("/{id}/real-name-audit")
    public Result<Void> auditRealName(@PathVariable Long id, @Valid @RequestBody RealNameAuditDTO dto) {
        sysUserAdminService.auditRealName(id, dto.getPass(), dto.getRemark());
        // B5：发布审核事件（业务层监听并发送站内消息通知用户）
        eventPublisher.publishEvent(new com.dianfei.common.event.RealNameAuditedEvent(id, dto.getPass(), dto.getRemark()));
        return Result.success();
    }
}

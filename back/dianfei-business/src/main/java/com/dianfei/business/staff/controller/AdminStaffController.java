package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.dto.StaffAuditDTO;
import com.dianfei.business.staff.dto.StaffCreateDTO;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · 员工管理（B1）：新增+审核上岗、禁用、片区调整
 */
@Tag(name = "后台-员工管理")
@RestController
@RequestMapping("/api/admin/staff")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffService staffService;

    @Operation(summary = "员工分页查询")
    @GetMapping
    public Result<PageVO<BizStaff>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) Integer staffType,
                                         @RequestParam(required = false) Long areaId,
                                         @RequestParam(required = false) Integer auditStatus) {
        return Result.success(staffService.page(pageNum, pageSize, staffType, areaId, auditStatus));
    }

    @Operation(summary = "新增员工（进入待审核）")
    @OperLog(value = "新增员工", module = "员工管理")
    @PostMapping
    public Result<BizStaff> create(@Valid @RequestBody StaffCreateDTO dto) {
        return Result.success(staffService.create(dto));
    }

    @Operation(summary = "审核员工上岗")
    @OperLog(value = "审核员工", module = "员工管理")
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody StaffAuditDTO dto) {
        staffService.audit(id, dto);
        return Result.success();
    }

    @Operation(summary = "禁用/启用员工")
    @OperLog(value = "启停员工", module = "员工管理")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam boolean enable) {
        staffService.changeStatus(id, enable);
        return Result.success();
    }

    @Operation(summary = "调整员工片区")
    @OperLog(value = "调整员工片区", module = "员工管理")
    @PutMapping("/{id}/area")
    public Result<Void> transferArea(@PathVariable Long id, @RequestParam Long areaId) {
        staffService.transferArea(id, areaId);
        return Result.success();
    }
}

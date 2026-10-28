package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.dto.StaffApplyDTO;
import com.dianfei.business.staff.service.StaffApplyService;
import com.dianfei.business.staff.vo.StaffApplyVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作人员自助申请（G1，onsite01，契约 C-O1）：
 * POST /api/staff/apply 用户申请成为回收员/维修技术员（要求实名已通过，重复申请拦截）；
 * GET /api/staff/apply/status 申请状态（mine.vue 三态卡片数据源）。
 * 审核复用 AdminStaffController 既有接口，本控制器不改。
 */
@Tag(name = "用户端-工作人员申请")
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffApplyController {

    private final StaffApplyService staffApplyService;

    @Operation(summary = "提交工作人员申请（回收员/维修技术员；实名已通过；进行中申请拦截）")
    @OperLog(value = "工作人员申请", module = "用户端-工作人员")
    @PostMapping("/apply")
    public Result<Void> apply(@Valid @RequestBody StaffApplyDTO dto) {
        staffApplyService.apply(UserContext.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "工作人员申请状态（未申请 applied=false）")
    @GetMapping("/apply/status")
    public Result<StaffApplyVO> status() {
        return Result.success(staffApplyService.status(UserContext.getUserId()));
    }
}

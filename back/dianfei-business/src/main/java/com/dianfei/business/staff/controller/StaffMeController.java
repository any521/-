package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.dto.AreaChangeDTO;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端 · 个人自助：员工登录后自行切换负责片区（接单/列表即时按新片区生效）
 */
@Tag(name = "员工端-个人自助")
@RestController
@RequestMapping("/api/staff/me")
@RequiredArgsConstructor
public class StaffMeController {

    private final StaffService staffService;

    @Operation(summary = "自助切换片区（校验目标片区启用）")
    @PutMapping("/area")
    public Result<Void> changeArea(@Valid @RequestBody AreaChangeDTO dto) {
        staffService.changeMyArea(UserContext.getUserId(), dto.getAreaId());
        return Result.success();
    }
}

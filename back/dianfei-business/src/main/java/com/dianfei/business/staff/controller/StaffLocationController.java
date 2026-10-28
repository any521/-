package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.dto.StaffLocationDTO;
import com.dianfei.business.staff.service.StaffLocationService;
import com.dianfei.business.staff.vo.StaffLocationVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工实时位置（GapFill B2，契约 C3）：
 * PUT /api/staff/location 员工接单后周期上报；GET /api/recycle/orders/{id}/staff-location 用户端查询接单员工位置
 * （端点路径冻结为回收订单域，仅回收订单；无接单/无上报 data=null）
 */
@Tag(name = "员工端-位置上报")
@RestController
@RequiredArgsConstructor
public class StaffLocationController {

    private final StaffLocationService locationService;

    @Operation(summary = "上报当前位置（接单后每 30s；Redis 存 2h）")
    @PutMapping("/api/staff/location")
    public Result<Void> report(@Valid @RequestBody StaffLocationDTO dto) {
        locationService.report(UserContext.getUserId(), dto.getLng(), dto.getLat());
        return Result.success();
    }

    @Operation(summary = "回收订单接单员工实时位置（无接单/无上报返回 data=null）")
    @GetMapping("/api/recycle/orders/{id}/staff-location")
    public Result<StaffLocationVO> staffLocation(@PathVariable Long id) {
        return Result.success(locationService.getByRecycleOrder(id, UserContext.getUserId()));
    }
}

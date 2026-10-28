package com.dianfei.dispatch.controller;

import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import com.dianfei.dispatch.dto.DispatchPlanDTO;
import com.dianfei.dispatch.service.DispatchPlanService;
import com.dianfei.dispatch.vo.DispatchPlanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F4 智能派单（12 章 POST /api/dispatch/plan）：Timefold VRPTW 求解当日路线，
 * 支持预览与直接指派两种模式；求解失败自动降级距离排序（Agent REDISPATCH
 * 决策单白名单亦指向本路径，见 DecisionExecutor.WHITELIST_URIS）。
 */
@Tag(name = "调度-智能派单（F4）")
@RestController
@RequestMapping("/api/dispatch")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchPlanService dispatchPlanService;

    @Operation(summary = "VRPTW 派单求解：片区→员工路线→停靠点（apply=true 直接指派）")
    @PostMapping("/plan")
    public Result<DispatchPlanVO> plan(@Valid @RequestBody DispatchPlanDTO dto) {
        return Result.success(dispatchPlanService.plan(dto));
    }
}

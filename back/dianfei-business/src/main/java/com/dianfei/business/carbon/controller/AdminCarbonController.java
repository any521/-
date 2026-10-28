package com.dianfei.business.carbon.controller;

import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonAdjustCmd;
import com.dianfei.business.carbon.dto.CarbonAdjustDTO;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.dto.CarbonRuleSaveDTO;
import com.dianfei.business.carbon.entity.CarbonRule;
import com.dianfei.business.carbon.service.CarbonAnchorService;
import com.dianfei.business.carbon.service.CarbonRuleService;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 后台 · 碳积分管理（B2）：规则配置（PUT /api/admin/carbon-rule 幂等 upsert）、
 * 积分调整（补发/扣减/冻结/解冻/清零，@OperLog 留痕）、手动补锚
 */
@Tag(name = "后台-碳积分管理")
@RestController
@RequestMapping("/api/admin")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminCarbonController {

    private final CarbonRuleService ruleService;
    private final CarbonLedgerOps ledgerOps;
    private final CarbonAnchorService anchorService;

    @Operation(summary = "规则列表（可按业务类型过滤）")
    @GetMapping("/carbon-rule")
    public Result<List<CarbonRule>> listRules(@RequestParam(required = false) String orderType) {
        return Result.success(ruleService.list(orderType));
    }

    @Operation(summary = "配置积分规则（orderType+category 幂等 upsert，零代码新增品类）")
    @OperLog(value = "配置碳积分规则", module = "碳积分管理")
    @PutMapping("/carbon-rule")
    public Result<CarbonRule> saveRule(@Valid @RequestBody CarbonRuleSaveDTO dto) {
        return Result.success(ruleService.save(dto));
    }

    @Operation(summary = "积分调整：补发（points>0）/ 违规扣减（points<0）")
    @OperLog(value = "积分调整", module = "碳积分管理")
    @PostMapping("/carbon/adjust")
    public Result<CarbonLedgerResult> adjust(@Valid @RequestBody CarbonAdjustDTO dto) {
        OrderTypeEnum orderType = null;
        if (dto.getOrderType() != null && !dto.getOrderType().isBlank()) {
            orderType = OrderTypeEnum.of(dto.getOrderType());
            if (orderType == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "orderType 不合法");
            }
        }
        return Result.success(ledgerOps.adjust(CarbonAdjustCmd.builder()
                .userId(dto.getUserId())
                .orderType(orderType)
                .orderId(dto.getOrderId())
                .points(dto.getPoints())
                .reason(dto.getReason())
                .build()));
    }

    @Operation(summary = "积分清零（违规处置）")
    @OperLog(value = "积分清零", module = "碳积分管理")
    @PostMapping("/carbon/clear")
    public Result<CarbonLedgerResult> clear(@RequestParam Long userId,
                                            @RequestParam(required = false) String reason) {
        return Result.success(ledgerOps.clear(userId, reason));
    }

    @Operation(summary = "积分冻结（可用余额转冻结，风控处置）")
    @OperLog(value = "积分冻结", module = "碳积分管理")
    @PostMapping("/carbon/freeze")
    public Result<CarbonLedgerResult> freeze(@RequestParam Long userId,
                                             @RequestParam Long points,
                                             @RequestParam String requestId,
                                             @RequestParam(required = false) String reason) {
        return Result.success(ledgerOps.freeze(userId, points, reason, requestId));
    }

    @Operation(summary = "积分解冻（冻结返还可用余额）")
    @OperLog(value = "积分解冻", module = "碳积分管理")
    @PostMapping("/carbon/unfreeze")
    public Result<CarbonLedgerResult> unfreeze(@RequestParam Long userId,
                                               @RequestParam Long points,
                                               @RequestParam String requestId,
                                               @RequestParam(required = false) String reason) {
        return Result.success(ledgerOps.unfreeze(userId, points, reason, requestId));
    }

    @Operation(summary = "手动触发指定日期 Merkle 锚定（补锚，幂等）")
    @OperLog(value = "手动锚定", module = "碳积分管理")
    @PostMapping("/carbon/anchor")
    public Result<?> anchor(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(anchorService.anchorFor(date));
    }
}

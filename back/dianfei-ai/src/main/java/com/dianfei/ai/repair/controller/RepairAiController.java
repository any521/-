package com.dianfei.ai.repair.controller;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.repair.dto.RepairDiagnoseDTO;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * F2 维修智能预诊直连入口（12 章 POST /api/ai/repair/diagnose）：
 * 用户下单前先问诊（订单内随单预诊由 RestRepairDiagnoseClient 走 SPI）；
 * AI 不可用返回 3001，前端降级为自由文字描述（D15）。
 */
@Slf4j
@Tag(name = "AI-维修预诊（F2）")
@RestController
@RequestMapping("/api/ai/repair")
@RequiredArgsConstructor
public class RepairAiController {

    private final AiServiceClient aiServiceClient;

    @Operation(summary = "故障预诊：可能故障/配件/可修复概率/处置建议（RAG+LLM，规则兜底）")
    @PostMapping("/diagnose")
    public Result<AiContracts.DiagnoseResponse> diagnose(@Valid @RequestBody RepairDiagnoseDTO dto) {
        try {
            return Result.success(aiServiceClient.repairDiagnose(AiContracts.DiagnoseRequest.builder()
                    .orderId(dto.getOrderId())
                    .deviceType(dto.getDeviceType())
                    .brand(dto.getBrand())
                    .faultDesc(dto.getFaultDesc())
                    .imageUrls(dto.getImageUrls() == null ? List.of() : dto.getImageUrls())
                    .build()));
        } catch (AiServiceException e) {
            log.warn("[F2] 预诊不可用 fallback={} : {}", e.getFallback(), e.getMessage());
            return Result.failed(ResultCode.AI_SERVICE_UNAVAILABLE,
                    "预诊暂不可用，请直接文字描述故障，技术员上门检测");
        }
    }
}

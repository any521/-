package com.dianfei.ai.forecast.controller;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * F9 时空预测（12 章 GET /api/ai/forecast/building）：楼栋/片区未来 72h 回收量与
 * 厨余量预测（Prophet 未接入前 Python 侧走移动平均兜底），供派单与运营大屏；
 * AI 不可用返回空序列 + degraded，调用方按无预测数据处理（D15，不阻塞）。
 */
@Slf4j
@Tag(name = "AI-时空预测（F9）")
@RestController
@RequestMapping("/api/ai/forecast")
@RequiresRole({"ADMIN", "RECYCLER", "SANITATION"})
@RequiredArgsConstructor
public class ForecastAiController {

    private final AiServiceClient aiServiceClient;

    @Operation(summary = "楼栋/片区量预测：series 6h 粒度 + 片区热力等级 1-5")
    @GetMapping("/building")
    public Result<AiContracts.ForecastResponse> building(
            @RequestParam Long areaId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(defaultValue = "RECYCLE") String target,
            @RequestParam(defaultValue = "72") Integer horizonHours) {
        try {
            return Result.success(aiServiceClient.forecast(AiContracts.ForecastRequest.builder()
                    .areaId(areaId)
                    .buildingId(buildingId)
                    .target(target)
                    .horizonHours(horizonHours)
                    .build()));
        } catch (AiServiceException e) {
            log.warn("[F9] 预测不可用 fallback={} : {}", e.getFallback(), e.getMessage());
            AiContracts.ForecastResponse degraded = new AiContracts.ForecastResponse();
            degraded.setSeries(List.of());
            degraded.setModelDesc("unavailable");
            degraded.setHotLevel(3);
            degraded.setDegraded(true);
            return Result.success(degraded);
        }
    }
}

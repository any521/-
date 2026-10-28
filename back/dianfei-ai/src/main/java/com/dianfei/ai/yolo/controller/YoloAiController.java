package com.dianfei.ai.yolo.controller;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F6 YOLO 垃圾图片检测（V44 F7 升级新增）
 * <p>POST /api/ai/yolo/detect → ai-back /v1/yolo/detect
 * - 输入：base64 图片
 * - 输出：标注图 base64 + 检测结果列表
 */
@Slf4j
@Tag(name = "AI-YOLO 图片检测")
@RestController
@RequestMapping("/api/ai/yolo")
@RequiredArgsConstructor
public class YoloAiController {

    private final AiServiceClient aiServiceClient;

    @Operation(summary = "YOLO 图片检测：上传 base64 图片 → 返回带框的标注图 + 识别结果")
    @PostMapping("/detect")
    public Result<AiContracts.YoloDetectResponse> detect(@Valid @RequestBody AiContracts.YoloDetectRequest dto) {
        return Result.success(aiServiceClient.yoloDetect(dto));
    }
}

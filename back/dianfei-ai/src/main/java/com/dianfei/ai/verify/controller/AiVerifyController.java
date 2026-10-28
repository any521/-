package com.dianfei.ai.verify.controller;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.verify.AiBuild3dTaskService;
import com.dianfei.ai.verify.AiVerifyService;
import com.dianfei.ai.verify.dto.AiVerifyDTO;
import com.dianfei.ai.verify.dto.Build3dDTO;
import com.dianfei.ai.verify.vo.AiVerifyVO;
import com.dianfei.ai.verify.vo.Build3dTaskVO;
import cn.hutool.core.util.StrUtil;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * F1 AI 视觉审核官（12 章 /api/ai/verify）：
 * 主应用编排入口 → AiVerifyService 三层管线（pHash 防重 + 视觉大模型 + 阈值决策）；
 * AI 宕机/超时/熔断由 service 内部转 MANUAL_REVIEW，本接口不抛 AI 异常、不阻塞回收链路
 */
@Tag(name = "AI-视觉审核（F1）")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiVerifyController {

    private final AiVerifyService aiVerifyService;
    private final AiBuild3dTaskService aiBuild3dTaskService;

    @Operation(summary = "图片三层审核：AUTO_PASS 自动通过 / MANUAL_REVIEW 冻结待人审 / REJECT 驳回")
    @PostMapping("/verify")
    public Result<AiVerifyVO> verify(@Valid @RequestBody AiVerifyDTO dto) {
        return Result.success(aiVerifyService.verify(dto, UserContext.getUserId()));
    }

    @Operation(summary = "提交 3D 模型构建任务（img2threejs 风格）：立即返回 taskId，前端轮询状态；失败回退预设模型库")
    @PostMapping("/verify/3d-build")
    public Result<Build3dTaskVO> build3d(@Valid @RequestBody Build3dDTO dto) {
        // mode 空/非法按 standard 处理（契约 C4-1）；非法值由 ai-back 侧兜底
        String mode = StrUtil.blankToDefault(dto.getMode(), "standard");
        return Result.success(aiBuild3dTaskService.submit(UserContext.getUserId(), dto.getImageUrl(), mode));
    }

    @Operation(summary = "查询 3D 构建任务状态（RUNNING/DONE/FAILED）")
    @GetMapping("/verify/3d-build/{taskId}")
    public Result<Build3dTaskVO> build3dStatus(@PathVariable String taskId) {
        Build3dTaskVO vo = aiBuild3dTaskService.get(UserContext.getUserId(), taskId);
        return vo == null
                ? Result.failed(ResultCode.FAILED, "3D 构建任务不存在或已过期")
                : Result.success(vo);
    }
}

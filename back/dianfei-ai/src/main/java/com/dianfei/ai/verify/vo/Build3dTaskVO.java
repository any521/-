package com.dianfei.ai.verify.vo;

import com.dianfei.ai.client.dto.AiContracts;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 3D 构建任务视图（/api/ai/verify/3d-build 轮询）：
 * status=RUNNING 时 spec 为空，前端以进度条过渡；DONE 后 spec 可能为 null
 * （LLM 未识别出可构建物品，前端回退预设模型库）。
 */
@Data
@Schema(description = "3D 构建任务状态")
public class Build3dTaskVO {

    @Schema(description = "任务 ID（UUID）")
    private String taskId;

    /** RUNNING / DONE / FAILED */
    @Schema(description = "任务状态：RUNNING 构建中 / DONE 完成（spec 可能为空）/ FAILED 失败")
    private String status;

    @Schema(description = "构建结果 spec（img2threejs 风格；DONE 且为空=回退预设模型库）")
    private AiContracts.Build3dSpec spec;

    @Schema(description = "失败原因（FAILED 时）")
    private String error;

    @Schema(description = "实际执行模式（extreme 降级时回显 advanced）")
    private String mode;
}

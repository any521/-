package com.dianfei.business.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 会话创建/复用入参（C-O3-2：按 bizType + bizId 幂等） */
@Data
@Schema(description = "IM 会话创建入参")
public class ImConversationCreateDTO {

    @Schema(description = "RECYCLE/REPAIR")
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    @Schema(description = "关联订单 ID")
    @NotNull(message = "订单 ID 不能为空")
    private Long bizId;
}

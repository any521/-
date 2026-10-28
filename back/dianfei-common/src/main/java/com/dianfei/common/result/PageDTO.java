package com.dianfei.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询入参（pageNum + pageSize）
 */
@Data
@Schema(description = "分页查询参数")
public class PageDTO {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页最少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private Integer pageSize = 10;
}

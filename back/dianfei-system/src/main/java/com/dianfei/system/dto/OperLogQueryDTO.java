package com.dianfei.system.dto;

import com.dianfei.common.result.PageDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志分页查询参数")
public class OperLogQueryDTO extends PageDTO {

    @Schema(description = "业务模块")
    private String module;

    @Schema(description = "操作人")
    private String operName;

    @Schema(description = "0 成功 / 1 失败")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}

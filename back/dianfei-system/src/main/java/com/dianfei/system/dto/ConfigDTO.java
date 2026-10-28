package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 参数配置新增/编辑入参
 */
@Data
@Schema(description = "参数配置新增/编辑参数")
public class ConfigDTO {

    @Schema(description = "参数 id，编辑时必传")
    private Long id;

    @Schema(description = "参数键，如 kitchen.report.start-hour")
    @NotBlank(message = "参数键不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9._\\-]{1,63}$", message = "参数键须为小写字母/数字/点/中划线/下划线，2-64 位")
    private String configKey;

    @Schema(description = "参数值")
    @NotBlank(message = "参数值不能为空")
    private String configValue;

    @Schema(description = "参数名称")
    @NotBlank(message = "参数名称不能为空")
    private String configName;

    @Schema(description = "备注")
    private String remark;
}

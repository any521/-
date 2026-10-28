package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 字典类型新增/编辑入参
 */
@Data
@Schema(description = "字典类型新增/编辑参数")
public class DictTypeDTO {

    @Schema(description = "字典类型 id，编辑时必传")
    private Long id;

    @Schema(description = "字典类型编码，如 recycle_category")
    @NotBlank(message = "字典类型编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "字典类型须为小写字母/数字/下划线，2-64 位")
    private String dictType;

    @Schema(description = "字典名称")
    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "0 停用 / 1 启用")
    private Integer status = 1;
}

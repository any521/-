package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典数据新增/编辑入参
 */
@Data
@Schema(description = "字典数据新增/编辑参数")
public class DictDataDTO {

    @Schema(description = "字典数据 id，编辑时必传")
    private Long id;

    @Schema(description = "所属字典类型编码")
    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    @Schema(description = "显示名，如 金属")
    @NotBlank(message = "字典标签不能为空")
    private String dictLabel;

    @Schema(description = "值，如 METAL")
    @NotBlank(message = "字典值不能为空")
    private String dictValue;

    @Schema(description = "排序")
    private Integer sort = 0;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "0 停用 / 1 启用")
    private Integer status = 1;
}

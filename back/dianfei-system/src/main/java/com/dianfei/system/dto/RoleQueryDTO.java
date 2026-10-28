package com.dianfei.system.dto;

import com.dianfei.common.result.PageDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色分页查询参数")
public class RoleQueryDTO extends PageDTO {

    @Schema(description = "角色名称/编码模糊搜索")
    private String keyword;

    @Schema(description = "0 停用 / 1 启用")
    private Integer status;
}

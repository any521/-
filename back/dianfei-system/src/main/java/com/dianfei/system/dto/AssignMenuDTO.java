package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色分配菜单入参
 */
@Data
@Schema(description = "角色分配菜单参数")
public class AssignMenuDTO {

    @Schema(description = "角色 id")
    @NotNull(message = "角色 id 不能为空")
    private Long roleId;

    @Schema(description = "菜单 id 集合（全量覆盖）")
    @NotNull(message = "菜单集合不能为空")
    private List<Long> menuIds;
}

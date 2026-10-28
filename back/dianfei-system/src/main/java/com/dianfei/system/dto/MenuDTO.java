package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 菜单新增/编辑入参
 */
@Data
@Schema(description = "菜单新增/编辑参数")
public class MenuDTO {

    @Schema(description = "菜单 id，编辑时必传")
    private Long id;

    @Schema(description = "父菜单 id，0 为根")
    private Long parentId = 0L;

    @Schema(description = "菜单名称")
    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    @Schema(description = "1 目录 / 2 菜单 / 3 按钮")
    @NotNull(message = "菜单类型不能为空")
    private Integer menuType;

    @Schema(description = "前端路由路径")
    private String path;

    @Schema(description = "前端组件")
    private String component;

    @Schema(description = "权限码，如 system:role:add")
    private String perm;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sort = 0;

    @Schema(description = "0 隐藏 / 1 显示")
    private Integer visible = 1;

    @Schema(description = "0 停用 / 1 启用")
    private Integer status = 1;
}

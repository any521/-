package com.dianfei.system.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（菜单管理 / profile 动态路由共用）
 */
@Data
@Schema(description = "菜单树节点")
public class MenuTreeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private String menuName;

    /** 1 目录 / 2 菜单 / 3 按钮 */
    private Integer menuType;

    private String path;

    private String component;

    private String perm;

    private String icon;

    private Integer sort;

    /** 0 隐藏 / 1 显示 */
    private Integer visible;

    /** 0 停用 / 1 启用 */
    private Integer status;

    private List<MenuTreeVO> children = new ArrayList<>();
}

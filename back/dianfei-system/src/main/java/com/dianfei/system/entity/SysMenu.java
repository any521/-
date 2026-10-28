package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单/权限（menu_type：1 目录 / 2 菜单 / 3 按钮）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 父菜单 id，0 为根 */
    private Long parentId;

    private String menuName;

    /** 1 目录 / 2 菜单 / 3 按钮 */
    private Integer menuType;

    /** 前端路由路径 */
    private String path;

    /** 前端组件 */
    private String component;

    /** 权限码，如 system:role:add */
    private String perm;

    private String icon;

    private Integer sort;

    /** 0 隐藏 / 1 显示 */
    private Integer visible;

    /** 0 停用 / 1 启用 */
    private Integer status;
}

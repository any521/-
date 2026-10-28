package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色编码，如 ADMIN，对应 sys_user.role */
    private String roleCode;

    private String roleName;

    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status;
}

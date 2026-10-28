package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户（身份三分之 HUMAN 主账号表；AGENT 系统账号亦落此表，principal_type=AGENT）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /** BCrypt 密文 */
    private String password;

    private String nickname;

    private String email;

    private String avatar;

    /** 角色编码：USER / RECYCLER / SANITATION / TECHNICIAN / ADMIN / AGENT */
    private String role;

    /** 员工所属片区（@DataScope 数据权限依据），非员工为空 */
    private Long areaId;

    /** 主体类型：HUMAN / AGENT（对齐 04.8 身份三分） */
    private String principalType;

    /** 实名姓名 */
    private String realName;

    /** 身份证号（实名认证） */
    private String idCard;

    /** 实名状态 0未认证 1待审 2已通过 3已驳回（GapFill B1，V17） */
    private Integer realNameStatus;

    /** 审核备注（驳回原因，GapFill B1，V17） */
    private String realNameRemark;

    /** 0 禁用 / 1 正常 */
    private Integer status;
}

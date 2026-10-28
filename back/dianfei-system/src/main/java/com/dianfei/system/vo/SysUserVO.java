package com.dianfei.system.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统用户分页项（管理端，G1）：roleName 联 sys_role.role_name，查不到回退角色码
 */
@Data
@Builder
@Schema(description = "系统用户分页项")
public class SysUserVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String nickname;

    private String email;

    /** 头像 URL（Upgrade U1：/api/file/preview/avatar/** 或外链，无则前端图标兜底） */
    private String avatar;

    /** 角色编码：USER / RECYCLER / SANITATION / TECHNICIAN / ADMIN / AGENT */
    private String role;

    /** 角色中文名 */
    private String roleName;

    /** 0 停用 / 1 正常 */
    private Integer status;

    /** 实名状态 0未认证 1待审 2已通过 3已驳回（GapFill B1，V17） */
    private Integer realNameStatus;

    /** 审核备注（驳回原因，GapFill B1，V17） */
    private String realNameRemark;

    /** yyyy-MM-dd HH:mm:ss */
    private String createTime;
}

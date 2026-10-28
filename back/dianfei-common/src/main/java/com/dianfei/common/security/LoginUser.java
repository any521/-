package com.dianfei.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录主体（身份三分：HUMAN / AGENT / MCP，对齐 04.8）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    public static final String PRINCIPAL_HUMAN = "HUMAN";
    public static final String PRINCIPAL_AGENT = "AGENT";
    public static final String PRINCIPAL_MCP = "MCP";

    private Long userId;
    private String username;
    /** 角色编码：USER / RECYCLER / SANITATION / TECHNICIAN / ADMIN 等 */
    private String role;
    /** 员工所属片区（@DataScope 数据权限依据） */
    private Long areaId;
    /** 主体类型：HUMAN / AGENT / MCP */
    private String principalType;
    /** 客户端类型：USER / STAFF / ADMIN */
    private String clientType;
}

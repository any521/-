package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 第三方 OAuth2 客户端（授权码模式，对齐 04.8 / 08 章；授权服务端点由 Track E 实现）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oauth_client")
public class SysOauthClient extends BaseEntity {

    private String clientId;

    /** BCrypt 密文 */
    private String clientSecret;

    private String clientName;

    /** 授权回调地址，多个逗号分隔 */
    private String redirectUri;

    /** 授权范围，如 order:read,order:write，多个逗号分隔 */
    private String scopes;

    /** 每分钟限流（默认 60 req/min） */
    private Integer rateLimit;

    /** 0 禁用 / 1 正常 */
    private Integer status;
}

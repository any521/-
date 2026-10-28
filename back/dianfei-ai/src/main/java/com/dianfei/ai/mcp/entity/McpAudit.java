package com.dianfei.ai.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * F10 MCP 调用审计（biz_mcp_audit，08 章）：
 * 全量工具调用逐条落审计并以 HashChainUtil 链接（curr = SHA256(prev + payload)）；
 * 下单类工具 need_confirm=1，confirm_state 由用户端二次确认回写（哈希仅覆盖调用时快照）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mcp_audit")
public class McpAudit extends BaseEntity {

    /** 待确认 */
    public static final int CONFIRM_PENDING = 0;
    /** 已确认执行 */
    public static final int CONFIRM_APPROVED = 1;
    /** 已拒绝/超时 */
    public static final int CONFIRM_REJECTED = 2;
    /** 已被抢占，正在执行 */
    public static final int CONFIRM_PROCESSING = 3;

    /** OAuth2 客户端（sys_oauth_client.client_id） */
    private String clientId;

    /** 被代理的用户 */
    private Long userId;

    /** MCP 工具名：query_carbon_account / book_recycling / … */
    private String toolName;

    /** 命中作用域 */
    private String scope;

    /** 调用入参快照 */
    private String paramsJson;

    /** 业务结果码（200 成功 / 429 限流 / 403 越权…） */
    private Integer resultCode;

    /** 结果摘要 */
    private String resultBrief;

    /** 1=下单类工具需用户端二次确认 */
    private Integer needConfirm;

    /** 确认状态：0 待确认 1 已确认 2 已拒绝/超时（仅下单类） */
    private Integer confirmState;

    /** 审计链哈希 */
    private String currHash;

    private String prevHash;

    private String traceId;
}

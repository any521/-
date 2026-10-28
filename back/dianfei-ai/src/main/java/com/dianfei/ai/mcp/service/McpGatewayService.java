package com.dianfei.ai.mcp.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.mcp.McpTokenStore;
import com.dianfei.ai.mcp.McpToolRegistry;
import com.dianfei.ai.mcp.entity.McpAudit;
import com.dianfei.ai.mcp.mapper.McpAuditMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.HashChainUtil;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.system.entity.SysOauthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * F10 MCP 网关核心（08 章 / D17 身份三分之第三方入口）：
 * Bearer 令牌 → MCP principal 校验 → 作用域校验 → 单客户端限流（sys_oauth_client.rate_limit，
 * 默认 60 req/min）→ 工具执行/挂起二次确认 → 全量调用逐条哈希入审计链（biz_mcp_audit）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpGatewayService {

    private static final String BEARER_PREFIX = "Bearer ";
    /** 下单类工具二次确认时限：超时按已拒绝处理 */
    private static final Duration CONFIRM_TIMEOUT = Duration.ofMinutes(10);

    private final McpTokenStore tokenStore;
    private final McpOAuthService oauthService;
    private final McpToolExecutor toolExecutor;
    private final McpAuditMapper auditMapper;
    private final McpAuditClaimService auditClaimService;
    private final RedisUtils redisUtils;

    /** 解析并校验 Authorization: Bearer <mcp-token>，失败抛 401 */
    public McpTokenStore.McpGrant authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 MCP 访问令牌");
        }
        McpTokenStore.McpGrant grant = tokenStore.getToken(
                authorization.substring(BEARER_PREFIX.length()).trim());
        if (grant == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "MCP 令牌无效或已撤销");
        }
        return grant;
    }

    /**
     * 工具调用主流程：读类工具直接执行返回；下单类工具落审计（need_confirm=1）后
     * 返回待确认单号，由用户端 /api/mcp/confirmations 二次确认后执行。
     * 注意：本方法不加事务——403/429 拒绝路径先写审计再抛异常，审计必须留痕不可回滚。
     */
    public Map<String, Object> call(McpTokenStore.McpGrant grant, String toolName,
                                    Map<String, Object> params) {
        McpToolRegistry.McpTool tool = McpToolRegistry.find(toolName)
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_ERROR, "未知 MCP 工具：" + toolName));

        // 作用域最小化：令牌授予集合必须覆盖工具作用域
        if (!grantedScopes(grant).contains(tool.scope())) {
            audit(grant, tool, params, 403, "作用域不足：需 " + tool.scope(), false, null);
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "令牌未授予 " + tool.scope() + " 作用域");
        }

        // 单客户端滑动分钟限流（memory.md：60 req/min，客户端可在 sys_oauth_client 单独配置）
        SysOauthClient client = oauthService.requireClient(grant.getClientId());
        int rateLimit = client.getRateLimit() == null ? 60 : client.getRateLimit();
        String rateKey = "mcp:rate:" + grant.getClientId() + ":" + (System.currentTimeMillis() / 60_000);
        if (redisUtils.increment(rateKey, Duration.ofMinutes(1)) > rateLimit) {
            audit(grant, tool, params, 429, "超过 " + rateLimit + " req/min 限流", false, null);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "MCP 客户端限流：" + rateLimit + " req/min");
        }

        // 下单类工具：挂起待用户二次确认（08 章权限最小化）
        if (tool.needConfirm()) {
            McpAudit pending = audit(grant, tool, params, 200,
                    "待用户二次确认", true, McpAudit.CONFIRM_PENDING);
            return Map.of("status", "PENDING_CONFIRMATION",
                    "confirmation_id", pending.getId(),
                    "expires_in_seconds", CONFIRM_TIMEOUT.toSeconds(),
                    "message", "请在点废成金 App「授权确认」中确认本次 " + tool.name() + " 操作");
        }

        Object data = toolExecutor.execute(tool.name(), grant.getUserId(), params);
        audit(grant, tool, params, 200, brief(data), false, null);
        return Map.of("status", "OK", "data", data);
    }

    /** 用户待确认列表（登录用户查询自己名下挂起的下单类调用） */
    public List<McpAudit> pendingConfirmations(Long userId) {
        return auditMapper.selectList(new LambdaQueryWrapper<McpAudit>()
                .eq(McpAudit::getUserId, userId)
                .eq(McpAudit::getNeedConfirm, 1)
                .eq(McpAudit::getConfirmState, McpAudit.CONFIRM_PENDING)
                .ge(McpAudit::getCreateTime, LocalDateTime.now().minus(CONFIRM_TIMEOUT))
                .orderByDesc(McpAudit::getId));
    }

    /** 用户二次确认/拒绝：确认即按调用时参数快照执行工具（哈希只覆盖调用快照，确认态可回写） */
    public Map<String, Object> confirm(Long userId, Long auditId, boolean approve) {
        McpAudit pending = auditMapper.selectById(auditId);
        if (pending == null || !userId.equals(pending.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "确认单不存在或不属于当前用户");
        }
        if (pending.getNeedConfirm() == null || pending.getNeedConfirm() != 1
                || pending.getConfirmState() == null
                || pending.getConfirmState() != McpAudit.CONFIRM_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "确认单已处理");
        }
        if (pending.getCreateTime() != null
                && pending.getCreateTime().plus(CONFIRM_TIMEOUT).isBefore(LocalDateTime.now())) {
            pending.setConfirmState(McpAudit.CONFIRM_REJECTED);
            pending.setResultBrief("确认超时，自动拒绝");
            auditMapper.updateById(pending);
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "确认已超时");
        }
        if (!approve) {
            pending.setConfirmState(McpAudit.CONFIRM_REJECTED);
            pending.setResultBrief("用户拒绝执行");
            auditMapper.updateById(pending);
            return Map.of("status", "REJECTED");
        }
        if (!auditClaimService.claim(auditId, userId)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "确认单已被其他请求处理");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = JSONUtil.toBean(pending.getParamsJson(), Map.class);
        try {
            Object data = toolExecutor.execute(pending.getToolName(), userId, params);
            pending.setConfirmState(McpAudit.CONFIRM_APPROVED);
            pending.setResultBrief(brief(data));
            auditMapper.updateById(pending);
            return Map.of("status", "OK", "data", data);
        } catch (RuntimeException e) {
            pending.setConfirmState(McpAudit.CONFIRM_REJECTED);
            pending.setResultBrief("执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            auditMapper.updateById(pending);
            throw e;
        }
    }

    /** 全量审计入链：curr = SHA256(prev + 调用快照)，prev 取最近一条审计 */
    private McpAudit audit(McpTokenStore.McpGrant grant, McpToolRegistry.McpTool tool,
                           Map<String, Object> params, int resultCode, String resultBrief,
                           boolean needConfirm, Integer confirmState) {
        McpAudit row = new McpAudit();
        row.setClientId(grant.getClientId());
        row.setUserId(grant.getUserId());
        row.setToolName(tool.name());
        row.setScope(tool.scope());
        row.setParamsJson(JSONUtil.toJsonStr(params == null ? Map.of() : params));
        row.setResultCode(resultCode);
        row.setResultBrief(resultBrief);
        row.setNeedConfirm(needConfirm ? 1 : 0);
        row.setConfirmState(confirmState);
        row.setTraceId(MDC.get("traceId"));

        String lockKey = "mcp:audit:chain:lock";
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10))) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "审计链正在写入，请稍后重试");
        }
        try {
            // 全局串行化“取末条 + 插入”，否则并发请求会同时使用同一个 prev_hash，形成分叉链。
            McpAudit last = auditMapper.selectOne(new LambdaQueryWrapper<McpAudit>()
                    .orderByDesc(McpAudit::getId).last("LIMIT 1"));
            String prevHash = last == null ? HashChainUtil.GENESIS_HASH : last.getCurrHash();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientId", row.getClientId());
            payload.put("userId", row.getUserId());
            payload.put("toolName", row.getToolName());
            payload.put("scope", row.getScope());
            payload.put("paramsJson", row.getParamsJson());
            payload.put("resultCode", row.getResultCode());
            payload.put("traceId", row.getTraceId());
            row.setPrevHash(prevHash);
            row.setCurrHash(HashChainUtil.chainHash(prevHash, payload));
            auditMapper.insert(row);
            return row;
        } finally {
            redisUtils.deleteIfValue(lockKey, lockValue);
        }
    }

    private Set<String> grantedScopes(McpTokenStore.McpGrant grant) {
        return grant.getScopes() == null ? Set.of()
                : Set.copyOf(Arrays.asList(grant.getScopes().split(",")));
    }

    private String brief(Object data) {
        String json = JSONUtil.toJsonStr(data);
        return json.length() > 480 ? json.substring(0, 480) + "…" : json;
    }
}

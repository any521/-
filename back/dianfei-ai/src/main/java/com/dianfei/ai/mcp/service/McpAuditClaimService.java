package com.dianfei.ai.mcp.service;

import com.dianfei.ai.mcp.mapper.McpAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MCP 确认单的独立抢占事务，避免工具执行失败时回滚抢占状态。 */
@Service
@RequiredArgsConstructor
public class McpAuditClaimService {

    private final McpAuditMapper auditMapper;

    @Transactional
    public boolean claim(Long auditId, Long userId) {
        return auditMapper.claimPending(auditId, userId) == 1;
    }
}

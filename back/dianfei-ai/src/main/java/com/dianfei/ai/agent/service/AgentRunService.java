package com.dianfei.ai.agent.service;

import cn.hutool.json.JSONUtil;
import com.dianfei.ai.agent.dto.AgentRunDTO;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.business.agent.dto.DecisionSubmitDTO;
import com.dianfei.business.agent.entity.AgentDecision;
import com.dianfei.business.agent.service.AgentDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F8 Agent 运行编排（E5）：事件 → AI 服务 /v1/agent/run（LangGraph 同构图：
 * perceive→plan→act/guard→audit，Python 侧已带 RULE_ENGINE 兜底）→ 决策草稿
 * 落库执行框架。AI 旁路服务整体宕机（熔断/超时）时走 Java 侧同构规则兜底，
 * 运营中枢不因 AI 不可用停摆（D15）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunService {

    /** Java 侧规则兜底：事件类型 → 确定性最小动作（与 ai-back _RULE_ACTIONS 对齐） */
    private static final Map<String, AiContracts.DecisionDraft> RULE_FALLBACK = Map.of(
            "PENDING_ORDER", ruleDraft("REDISPATCH", "/api/dispatch/plan", "LOW",
                    "规则兜底：待派工单触发重排派单"),
            "RISK_ALERT", ruleDraft("FREEZE_POINTS", "/api/carbon/points/freeze", "HIGH",
                    "规则兜底：风控告警冻结可疑积分，须双人复核"),
            "USER_TICKET", ruleDraft("REPLY_TICKET", "/api/message/send", "LOW",
                    "规则兜底：客服工单生成标准回复"),
            "DAILY_METRICS", ruleDraft("NOTIFY", "/api/message/send", "LOW",
                    "规则兜底：经营指标聚合生成日报通知"));

    private final AiServiceClient aiServiceClient;
    // 决策单落库/执行统一走 business 模块唯一实现（Track E 重构：AI 模块只做编排，不做决策持久化）
    private final AgentDecisionService agentDecisionService;

    /** 触发一次 Agent 运行：返回落库后的决策单（含哈希与执行状态） */
    public Map<String, Object> run(AgentRunDTO dto) {
        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun());
        AiContracts.AgentRunResponse resp;
        try {
            resp = aiServiceClient.agentRun(buildRequest(dto));
        } catch (AiServiceException e) {
            log.warn("[F8] AI 服务不可用，Java 规则兜底运行 agent={} fallback={}",
                    dto.getAgentType(), e.getFallback());
            resp = ruleFallbackRun(dto);
        }
        List<AgentDecision> saved = agentDecisionService.submitBatch(
                dto.getAgentType(), toSubmitDTOs(resp.getDecisions()), dryRun);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentType", dto.getAgentType());
        result.put("summary", resp.getSummary());
        result.put("modelName", resp.getModelName());
        result.put("degraded", resp.getDegraded());
        result.put("dryRun", dryRun);
        result.put("decisions", saved);
        return result;
    }

    private AiContracts.AgentRunRequest buildRequest(AgentRunDTO dto) {
        List<AiContracts.AgentEvent> events = dto.getEvents().stream()
                .map(e -> AiContracts.AgentEvent.builder()
                        .eventType(e.getEventType())
                        .payload(e.getPayload() == null ? Map.of() : e.getPayload())
                        .build())
                .toList();
        return AiContracts.AgentRunRequest.builder()
                .agentType(dto.getAgentType())
                .events(events)
                .dryRun(Boolean.TRUE.equals(dto.getDryRun()))
                .build();
    }

    /** AI 旁路整体不可用：按事件类型映射确定性动作（degraded=true 全程留痕） */
    private AiContracts.AgentRunResponse ruleFallbackRun(AgentRunDTO dto) {
        List<AiContracts.DecisionDraft> drafts = new ArrayList<>();
        for (AgentRunDTO.Event event : dto.getEvents()) {
            AiContracts.DecisionDraft template = RULE_FALLBACK.get(event.getEventType());
            if (template == null) {
                continue;
            }
            AiContracts.DecisionDraft draft = new AiContracts.DecisionDraft();
            draft.setAgentType(dto.getAgentType());
            draft.setAction(template.getAction());
            draft.setTargetUri(template.getTargetUri());
            draft.setPayloadJson(event.getPayload() == null ? Map.of() : event.getPayload());
            draft.setRiskLevel(template.getRiskLevel());
            draft.setEvidences(template.getEvidences());
            drafts.add(draft);
        }
        AiContracts.AgentRunResponse resp = new AiContracts.AgentRunResponse();
        resp.setAgentType(dto.getAgentType());
        resp.setDecisions(drafts);
        resp.setSummary("Java 规则兜底规划 " + drafts.size() + " 个动作（AI 旁路不可用）");
        resp.setModelName("rule/java-fallback");
        resp.setDegraded(true);
        return resp;
    }

    private static AiContracts.DecisionDraft ruleDraft(String action, String uri,
                                                       String risk, String reason) {
        AiContracts.DecisionDraft draft = new AiContracts.DecisionDraft();
        draft.setAction(action);
        draft.setTargetUri(uri);
        draft.setPayloadJson(Map.of());
        draft.setRiskLevel(risk);
        draft.setEvidences(List.of(Map.of(
                "source", "RULE", "description", reason, "data_ref", "java-fallback")));
        return draft;
    }

    /** 决策草稿 → business 模块提交 DTO（agentId 由 submitBatch 统一覆写为运行主体） */
    private static List<DecisionSubmitDTO> toSubmitDTOs(List<AiContracts.DecisionDraft> drafts) {
        return drafts.stream().map(draft -> {
            DecisionSubmitDTO dto = new DecisionSubmitDTO();
            dto.setActionType(draft.getAction());
            dto.setTargetUri(draft.getTargetUri());
            dto.setParamsJson(JSONUtil.toJsonStr(draft.getPayloadJson()));
            dto.setEvidencesJson(JSONUtil.toJsonStr(draft.getEvidences()));
            dto.setRiskLevel(riskCode(draft.getRiskLevel()));
            return dto;
        }).toList();
    }

    /** LOW/MEDIUM/HIGH → 1低/2中/3高；未知风险从严按高风险双人复核 */
    private static int riskCode(String riskLevel) {
        return switch (riskLevel == null ? "" : riskLevel) {
            case "LOW" -> AgentDecision.RISK_LOW;
            case "MEDIUM" -> AgentDecision.RISK_MEDIUM;
            default -> AgentDecision.RISK_HIGH;
        };
    }
}

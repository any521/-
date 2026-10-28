package com.dianfei.ai.agent.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dianfei.business.agent.entity.AgentDecision;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.recycle.dispatch.RecycleDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * 决策单白名单执行器（06 章"行动可控" / D17，business 侧 {@code DecisionExecutor} 契约的真实实现）：
 * 审批通过（或低风险自动）后，框架代 Agent 调用与人类共用的业务服务；仅允许白名单 target_uri，
 * Agent 不持任何数据库/接口凭据，越权动作一律拒绝执行并回写失败。
 * {@code @Primary} 使全量应用注入本实现；business 模块独立测试（H2，无 AI 依赖）仍用 MockDecisionExecutor 兜底。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DecisionExecutor implements com.dianfei.business.agent.service.DecisionExecutor {

    /** Agent 接口白名单（对齐 06 章：只能使用平台既有 REST 的语义等价服务入口） */
    public static final Set<String> WHITELIST_URIS = Set.of(
            "/api/dispatch/plan",
            "/api/carbon/points/freeze",
            "/api/message/send");

    private final RecycleDispatchService recycleDispatchService;
    private final CarbonLedgerOps carbonLedgerOps;
    private final MessageSender messageSender;

    /**
     * 执行单条已批准决策，返回结果摘要（回写 execute_result）；
     * 失败/越权抛 IllegalStateException，由调用方置 STATUS_EXECUTE_FAILED
     */
    public String execute(AgentDecision decision) {
        if (decision.getTargetUri() == null || !WHITELIST_URIS.contains(decision.getTargetUri())) {
            throw new IllegalStateException("目标接口不在 Agent 白名单：" + decision.getTargetUri());
        }
        JSONObject params = JSONUtil.isTypeJSONObject(decision.getParamsJson())
                ? JSONUtil.parseObj(decision.getParamsJson()) : new JSONObject();
        return switch (decision.getActionType()) {
            case "REDISPATCH" -> redispatch(params);
            case "FREEZE_POINTS" -> freezePoints(decision, params);
            case "REPLY_TICKET", "NOTIFY" -> notifyUser(decision, params);
            default -> throw new IllegalStateException("不支持的动作类型：" + decision.getActionType());
        };
    }

    /** AGENT-01 重排派单：复用 B3 派单编排（幂等，只处理未指派单） */
    private String redispatch(JSONObject params) {
        LocalDate date;
        try {
            date = params.getStr("date") == null ? LocalDate.now() : LocalDate.parse(params.getStr("date"));
        } catch (DateTimeParseException e) {
            date = LocalDate.now();
        }
        int assigned = recycleDispatchService.dispatchDate(date);
        return "重排派单完成：" + date + " 成功指派 " + assigned + " 单";
    }

    /** AGENT-02 冻结可疑积分：走账本唯一入口（type=6 冻结流水，哈希入链） */
    private String freezePoints(AgentDecision decision, JSONObject params) {
        Long userId = params.getLong("user_id");
        Long points = params.getLong("points");
        if (userId == null || points == null || points <= 0) {
            throw new IllegalStateException("FREEZE_POINTS 缺少 user_id/points 参数");
        }
        carbonLedgerOps.freeze(userId, points,
                "Agent 风控冻结（决策单 " + decision.getId() + "）");
        return "已冻结用户 " + userId + " 积分 " + points;
    }

    /** AGENT-03/04 站内信回复与通知：复用消息通道 */
    private String notifyUser(AgentDecision decision, JSONObject params) {
        Long userId = params.getLong("user_id");
        if (userId == null) {
            throw new IllegalStateException("消息动作缺少 user_id 参数");
        }
        String title = params.getStr("title", "运营通知");
        String content = params.getStr("content", "您有一条来自智能运营中枢的消息");
        messageSender.send(userId, "AGENT_NOTIFY", title, content);
        return "已向用户 " + userId + " 发送消息：" + title;
    }
}

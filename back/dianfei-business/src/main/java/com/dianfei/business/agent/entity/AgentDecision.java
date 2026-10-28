package com.dianfei.business.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 决策单（biz_agent_decision，C5 / 对接 Track E F8，D17 全量入链）：
 * 风险分级 1低(自动执行) 2中(运营审批) 3高(双人复核)；决策内容逐条哈希链（prev/decision hash）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_agent_decision")
public class AgentDecision extends BaseEntity {

    /** 风险：低（自动执行） */
    public static final int RISK_LOW = 1;
    /** 风险：中（运营单人审批） */
    public static final int RISK_MEDIUM = 2;
    /** 风险：高（双人复核） */
    public static final int RISK_HIGH = 3;

    /** 待审批 */
    public static final int STATUS_PENDING = 0;
    /** 已通过（待/已执行） */
    public static final int STATUS_APPROVED = 1;
    /** 已驳回 */
    public static final int STATUS_REJECTED = 2;
    /** 已执行 */
    public static final int STATUS_EXECUTED = 3;
    /** 执行失败 */
    public static final int STATUS_EXECUTE_FAILED = 4;

    /** 发起 Agent（principal_type=AGENT 系统账号标识） */
    private String agentId;

    /** 动作类型：DISPATCH_ADJUST/PRICE_CHANGE/POINTS_ADJUST 等 */
    private String actionType;

    /** 目标资源 URI */
    private String targetUri;

    /** 动作参数快照 JSON */
    private String paramsJson;

    /** 依据链 JSON（source/description/dataRef 数组） */
    private String evidencesJson;

    /** 1低 2中 3高 */
    private Integer riskLevel;

    /** 0待审批 1已通过 2已驳回 3已执行 4执行失败 */
    private Integer status;

    /** 决策单内容哈希（入链） */
    private String decisionHash;

    /** 上一条决策哈希（链式） */
    private String prevHash;

    /** 一审人（中/高风险） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long firstApprover;

    private LocalDateTime firstApproveTime;

    /** 二审人（高风险双人复核） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long secondApprover;

    private LocalDateTime secondApproveTime;

    private String rejectReason;

    /** 执行结果回写 */
    private String executeResult;

    private LocalDateTime executeTime;
}

package com.dianfei.ai.config.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 能力路由（ai_route，V11 冻结 DDL）
 * <p>chain 依序尝试，全部失败走 fallback 降级
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_route", autoResultMap = true)
public class AiRoute extends BaseEntity {

    /** 能力标识：visual_verify/repair_diagnose/voice_parse/... */
    private String capability;

    /** 路由链 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ChainHop> chain;

    /** 链耗尽后降级代号：MANUAL_REVIEW/KNOWLEDGE_RULE/... */
    private String fallback;

    /** 单供应商出参校验失败重试次数 */
    private Integer maxRetries;

    /** 重试温度衰减系数 */
    private BigDecimal retryTemperatureDecay;

    /** 0禁用 1启用 */
    private Integer enabled;

    private String remark;

    /** 路由链节点 */
    @Data
    public static class ChainHop {
        /** 供应商 name（引用 ai_provider.name） */
        private String provider;
        /** 模型名（需在 provider.models 内） */
        private String model;
    }
}

package com.dianfei.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 旁路服务连接配置（application.yml dianfei.ai.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "dianfei.ai")
public class AiServiceProperties {

    /** ai-back 内网地址 */
    private String baseUrl = "http://127.0.0.1:8100";

    /** 内网服务间 Token（与 ai-back AI_INTERNAL_TOKEN 一致；空=开发模式） */
    private String internalToken = "";

    /** ai-back 回调拉取配置时的 X-Internal-Token 校验值（空=开发模式不校验） */
    private String configCallbackToken = "";

    /** 供应商密钥列 AES 加密密钥（16/24/32 字节；空=开发降级透传） */
    private String configAesKey = "";

    private int connectTimeoutMs = 2000;

    /** 读超时：F1 P95 ≤ 5s，留缓冲 8s（与 providers.json timeout_seconds 对齐量级） */
    private int readTimeoutMs = 8000;

    /**
     * 3D 构建读超时：spec JSON 生成（5-12 几何体）显著慢于识别，独立放宽避免误伤主链路快速失败。
     * 必须覆盖 ai-back 侧三档模式：standard ~70-90s / advanced ≤270s / extreme ≤600s（含渲染对比）；
     * 该值仅作极端上限，ai-back 内部对 standard 自管控 60s 级 LLM 超时。
     */
    private int build3dReadTimeoutMs = 620000;

    /**
     * CAM 精分类读超时：视觉 LLM 识别裁剪图（ai-back invoke 上限 30s），
     * 独立放宽至 15s 避免误伤普通接口快速失败（契约 C6-2）。
     */
    private int classifyReadTimeoutMs = 15000;

    /**
     * 回收市场行情读超时：联网搜索（≤8s）+ LLM 提炼（≤15s）串行，
     * 独立放宽避免 15s 上限误伤（B3 实时报价）。
     */
    private int marketPriceReadTimeoutMs = 60000;

    /**
     * 语音管家读超时：图片识别链路（YOLO 推理 ≤2s + RAG + LLM 生成 ≤15s）串行，
     * 独立放宽避免默认 8s 上限误伤（F7 语音/图片解析）。
     */
    private int voiceReadTimeoutMs = 60000;
}

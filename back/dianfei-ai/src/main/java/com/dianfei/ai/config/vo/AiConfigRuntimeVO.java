package com.dianfei.ai.config.vo;

import com.dianfei.ai.config.entity.AiRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 运行时配置（供 Python ai-back 拉取）
 * <p>Provider 的 apiKey 已解密（或从环境变量读取），Python 直接可用
 */
@Data
@Schema(description = "AI 运行时配置（内部接口）")
public class AiConfigRuntimeVO {

    @Schema(description = "启用的供应商列表")
    private List<Provider> providers;

    @Schema(description = "启用的能力路由")
    private List<AiRoute> routes;

    /**
     * 运行时供应商视图：apiKey 已解密，附带推导后的请求路径
     */
    @Data
    public static class Provider {
        private String name;
        private String vendor;
        /** openai/anthropic/responses/custom */
        private String protocol;
        private String baseUrl;
        /** 推导后的请求路径（protocol 默认值或自定义） */
        private String requestPath;
        /** 解密后的密钥（可能为空，如 mock 供应商） */
        private String apiKey;
        private List<String> models;
        private List<String> capabilities;
        private Map<String, String> extraHeaders;
        /** 请求方法：POST/GET（custom/responses 协议） */
        private String method;
        /** 请求体 JSON 模板（custom 协议） */
        private String bodyTemplate;
        /** 响应文本提取点路径 */
        private String responseTextPath;
        private Integer timeoutSeconds;
        /** 单次输出上限 token（长结构化 JSON 如 3D spec 建议 8192） */
        private Integer maxTokens;
        /** 联网搜索服务商：bocha/tavily；空=auto（仅 name=web-search 行生效） */
        private String webSearchProvider;
        /** 联网搜索启用开关：0停用 1启用（仅 name=web-search 行生效） */
        private Integer webSearchEnabled;
    }
}

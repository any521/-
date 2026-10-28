package com.dianfei.ai.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 供应商保存请求（管理端）
 * <p>apiKey 为明文（用户录入），service 层 AES 加密入库；
 * 编辑时不传 apiKey 表示不变更
 */
@Data
@Schema(description = "AI 供应商保存请求")
public class AiProviderDTO {

    @Schema(description = "编辑时传，新增不传")
    private Long id;

    @NotBlank
    @Schema(description = "供应商唯一名（如 qwen-vl）")
    private String name;

    @NotBlank
    @Schema(description = "厂商：alibaba/zhipu/deepseek/openai/anthropic/custom/internal")
    private String vendor;

    @NotBlank
    @Schema(description = "协议：openai/anthropic/custom")
    private String protocol;

    @NotBlank
    @Schema(description = "API 基址")
    private String baseUrl;

    @Schema(description = "请求路径；空=按协议推导")
    private String requestPath;

    @Schema(description = "密钥明文；编辑时不传表示不变更")
    private String apiKey;

    @Schema(description = "密钥环境变量名；非空时优先从环境变量读取")
    private String apiKeyEnv;

    @Schema(description = "模型列表")
    private List<String> models;

    @Schema(description = "能力标签：vision/chat/reasoning")
    private List<String> capabilities;

    @Schema(description = "自定义请求头")
    private Map<String, String> extraHeaders;

    @Schema(description = "请求方法：POST/GET（custom/responses 协议；默认 POST）")
    private String method;

    @Schema(description = "请求体 JSON 模板（custom 协议；占位符 {{model}}/{{messages}}/{{input}}/{{text}}/{{image}}/{{temperature}}）")
    private String bodyTemplate;

    @Schema(description = "响应文本提取点路径（如 output.0.content.0.text）")
    private String responseTextPath;

    @Schema(description = "超时秒数，默认 8")
    private Integer timeoutSeconds;

    @Schema(description = "单次输出上限 token，默认 4096；长结构化 JSON 建议 8192")
    private Integer maxTokens;

    @Schema(description = "0禁用 1启用")
    private Integer enabled;

    @Schema(description = "联网搜索服务商：bocha/tavily；空=auto（仅 name=web-search 行生效）")
    private String webSearchProvider;

    @Schema(description = "联网搜索启用开关：0停用 1启用（仅 name=web-search 行生效）")
    private Integer webSearchEnabled;

    @Schema(description = "排序")
    private Integer sort;

    private String remark;
}

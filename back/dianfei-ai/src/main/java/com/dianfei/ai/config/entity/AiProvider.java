package com.dianfei.ai.config.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * AI 供应商配置（ai_provider，V11 冻结 DDL + V12 协议扩展）
 * <p>协议兼容：protocol 决定请求格式
 * <ul>
 *   <li>openai —— /chat/completions + Authorization: Bearer {key}</li>
 *   <li>anthropic —— /v1/messages + x-api-key + anthropic-version</li>
 *   <li>responses —— 火山引擎 Ark Responses API（/responses，内置模板，支持多模态）</li>
 *   <li>custom —— method + request_path + body_template + response_text_path 全参数自定义</li>
 * </ul>
 * 密钥：api_key 列 AES 加密存储；api_key_env 非空时优先从环境变量读取
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_provider", autoResultMap = true)
public class AiProvider extends BaseEntity {

    /** 供应商唯一名（如 qwen-vl） */
    private String name;

    /** 厂商标识：alibaba/zhipu/deepseek/openai/anthropic/custom/internal */
    private String vendor;

    /** 请求协议：openai/anthropic/custom */
    private String protocol;

    /** API 基址（不含请求路径） */
    private String baseUrl;

    /** 请求路径；空=按协议推导 */
    private String requestPath;

    /** 密钥（AES 加密存储） */
    private String apiKey;

    /** 密钥环境变量名；非空时优先从环境变量读取 */
    private String apiKeyEnv;

    /** 支持的模型列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> models;

    /** 能力标签：vision/chat/reasoning */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> capabilities;

    /** 自定义请求头（custom 协议或需追加认证头） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extraHeaders;

    /** 请求方法：POST/GET（custom/responses 协议；默认 POST） */
    private String method;

    /** 请求体 JSON 模板（custom 协议；占位符 {{model}}/{{messages}}/{{input}}/{{text}}/{{image}}/{{temperature}}） */
    private String bodyTemplate;

    /** 响应文本提取点路径（如 output.0.content.0.text / choices.0.message.content） */
    private String responseTextPath;

    /** 调用超时（秒） */
    private Integer timeoutSeconds;

    /** 单次输出上限 token（长结构化 JSON 如 3D spec 建议 8192） */
    private Integer maxTokens;

    /** 0禁用 1启用 */
    private Integer enabled;

    /** 联网搜索服务商：bocha/tavily；空=auto（仅 name=web-search 行生效） */
    private String webSearchProvider;

    /** 联网搜索启用开关：0停用 1启用（仅 name=web-search 行生效） */
    private Integer webSearchEnabled;

    /** 展示排序 */
    private Integer sort;

    private String remark;
}

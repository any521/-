package com.dianfei.ai.client;

/**
 * AI 旁路服务调用失败（网络/超时/熔断开路/3001/3002 统一收口）。
 * 编排层捕获后执行降级路径，禁止直接透传给前端。
 */
public class AiServiceException extends RuntimeException {

    /** Python 侧返回的 fallback 代号（MANUAL_REVIEW / KNOWLEDGE_RULE / TEXT_INPUT…），可为 null */
    private final String fallback;

    public AiServiceException(String message, String fallback) {
        super(message);
        this.fallback = fallback;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.fallback = null;
    }

    public String getFallback() {
        return fallback;
    }
}

package com.dianfei.ai.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 旁路服务客户端装配（E0）：RestClient（连接/读超时）+ Resilience4j 熔断器。
 *
 * <p>熔断参数（对齐 04 章/D15）：滑动窗口 20 次调用，失败率 ≥50% 开路 30s，
 * 半开放 5 次试探；开路期间调用快速失败 → AiServiceException → 编排层降级，
 * 保证 AI 宕机时主链路响应时间不被拖长。
 */
@Configuration
public class AiClientConfig {

    @Bean
    public RestClient aiRestClient(AiServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /** 3D 构建专用客户端：spec 生成长 JSON 耗时长，读超时独立放宽（不影响 F1 快速失败） */
    @Bean
    public RestClient aiBuild3dRestClient(AiServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getBuild3dReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /** 精分类专用客户端（CAM）：LLM 识别慢于普通接口，读超时独立放宽至 15s（契约 C6-2） */
    @Bean
    public RestClient aiClassifyRestClient(AiServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getClassifyReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /** 回收市场行情专用客户端（B3）：联网搜索+LLM 提炼串行较慢，读超时独立放宽，避免误伤主链路快速失败 */
    @Bean
    public RestClient aiMarketPriceRestClient(AiServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getMarketPriceReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /** 语音管家专用客户端（F7）：YOLO 推理 + RAG + LLM 串行较慢（图片链路常超默认 8s），读超时独立放宽 */
    @Bean
    public RestClient aiVoiceRestClient(AiServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.getVoiceReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    public CircuitBreaker aiServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .build();
        return CircuitBreaker.of("ai-service", config);
    }
}

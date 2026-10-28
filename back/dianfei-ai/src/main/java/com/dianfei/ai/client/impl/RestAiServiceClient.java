package com.dianfei.ai.client.impl;

import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.AiServiceException;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.ai.config.AiServiceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link AiServiceClient} 默认实现（E0：RestClient + Resilience4j 熔断）。
 *
 * <p>降级语义（D15）：
 * <ul>
 *   <li>网络/超时/熔断开路/非 200 业务码 → 统一抛 {@link AiServiceException}，
 *       编排层据 {@link AiServiceException#getFallback()} 走各能力降级路径</li>
 *   <li>熔断器：失败率超阈值后开路快速失败，避免 AI 宕机拖垮主链路线程池</li>
 *   <li>X-Internal-Token / X-Trace-Id 在此统一附加，业务无感</li>
 * </ul>
 */
@Slf4j
@Component
public class RestAiServiceClient implements AiServiceClient {

    private static final int CODE_SUCCESS = 200;

    private final RestClient restClient;
    private final RestClient build3dRestClient;
    private final RestClient classifyRestClient;
    private final RestClient marketPriceRestClient;
    private final RestClient voiceRestClient;
    private final AiServiceProperties properties;
    private final CircuitBreaker circuitBreaker;

    public RestAiServiceClient(RestClient aiRestClient, RestClient aiBuild3dRestClient,
                               RestClient aiClassifyRestClient, RestClient aiMarketPriceRestClient,
                               RestClient aiVoiceRestClient,
                               AiServiceProperties properties,
                               CircuitBreaker aiServiceCircuitBreaker) {
        this.restClient = aiRestClient;
        this.build3dRestClient = aiBuild3dRestClient;
        this.classifyRestClient = aiClassifyRestClient;
        this.marketPriceRestClient = aiMarketPriceRestClient;
        this.voiceRestClient = aiVoiceRestClient;
        this.properties = properties;
        this.circuitBreaker = aiServiceCircuitBreaker;
    }

    @Override
    public AiContracts.VerifyResponse verifyImage(AiContracts.VerifyRequest request,
                                                  Double passThreshold, Double reviewThreshold) {
        StringBuilder uri = new StringBuilder("/v1/verify/image");
        if (passThreshold != null || reviewThreshold != null) {
            uri.append('?');
            if (passThreshold != null) {
                uri.append("pass_threshold=").append(passThreshold);
            }
            if (reviewThreshold != null) {
                if (passThreshold != null) {
                    uri.append('&');
                }
                uri.append("review_threshold=").append(reviewThreshold);
            }
        }
        return post(uri.toString(), request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.Build3dResponse build3d(AiContracts.Build3dRequest request) {
        return post(build3dRestClient, "/v1/verify/3d-build", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.ClassifyResponse classifyDetail(AiContracts.ClassifyRequest request) {
        // 专用客户端：读超时独立放宽至 15s（LLM 识别慢于普通接口，契约 C6-2）
        return post(classifyRestClient, "/v1/classify/detail", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.DiagnoseResponse repairDiagnose(AiContracts.DiagnoseRequest request) {
        return post("/v1/repair/diagnose", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.VoiceParseResponse voiceParse(AiContracts.VoiceParseRequest request) {
        // 专用客户端：图片识别链路（YOLO+RAG+LLM）常超默认 8s，读超时独立放宽至 voiceReadTimeoutMs（契约 F7）
        return post(voiceRestClient, "/v1/voice/parse", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.VoiceAsrResponse voiceAsr(AiContracts.VoiceAsrRequest request) {
        // 本地 vosk 转写快于 LLM 链路，沿用 voice 专用客户端即可
        return post(voiceRestClient, "/v1/voice/asr", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.YoloDetectResponse yoloDetect(AiContracts.YoloDetectRequest request) {
        return post("/v1/yolo/detect", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.ForecastResponse forecast(AiContracts.ForecastRequest request) {
        return post("/v1/forecast", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.PricingResponse pricing(AiContracts.PricingRequest request) {
        return post("/v1/pricing", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.MarketPriceResponse marketPrice(AiContracts.MarketPriceRequest request) {
        // 搜索+LLM 提炼较慢，独立客户端读超时 60s（B3 实时报价）
        return post(marketPriceRestClient, "/v1/market-price", request, new ParameterizedTypeReference<>() {
        });
    }

    @Override
    public AiContracts.AgentRunResponse agentRun(AiContracts.AgentRunRequest request) {
        return post("/v1/agent/run", request, new ParameterizedTypeReference<>() {
        });
    }

    /** 统一 POST：熔断包裹 → PyResult 解壳 → 非 200 抛 AiServiceException（含 fallback 代号） */
    private <T> T post(String uri, Object body,
                       ParameterizedTypeReference<AiContracts.PyResult<T>> typeRef) {
        return post(restClient, uri, body, typeRef);
    }

    private <T> T post(RestClient client, String uri, Object body,
                       ParameterizedTypeReference<AiContracts.PyResult<T>> typeRef) {
        Supplier<AiContracts.PyResult<T>> call = () -> client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Token", properties.getInternalToken())
                .header("X-Trace-Id", currentTraceId())
                .body(body)
                .retrieve()
                .body(typeRef);
        AiContracts.PyResult<T> result;
        try {
            result = circuitBreaker.executeSupplier(call);
        } catch (Exception e) {
            // 网络异常/超时/CallNotPermittedException（熔断开路）统一收口；
            // 附堆栈便于区分读超时（Read timed out）与响应提取失败（UnknownContentTypeException）
            log.warn("AI 服务调用失败 uri={} : {}", uri, e.getMessage(), e);
            throw new AiServiceException("AI 服务调用失败: " + uri, e);
        }
        if (result == null) {
            throw new AiServiceException("AI 服务返回空响应: " + uri, (String) null);
        }
        if (result.getCode() == null || result.getCode() != CODE_SUCCESS) {
            // 3001 时 msg 形如 "AI 路由链耗尽 fallback=MANUAL_REVIEW"
            throw new AiServiceException(
                    "AI 服务业务失败 code=" + result.getCode() + " msg=" + result.getMsg(),
                    extractFallback(result.getMsg()));
        }
        return result.getData();
    }

    /** 从 3001 响应 msg 中提取 fallback 代号（MANUAL_REVIEW / KNOWLEDGE_RULE / …） */
    private static String extractFallback(String msg) {
        if (msg == null) {
            return null;
        }
        int idx = msg.indexOf("fallback=");
        if (idx < 0) {
            return null;
        }
        String tail = msg.substring(idx + "fallback=".length()).trim();
        int end = tail.indexOf(' ');
        return end > 0 ? tail.substring(0, end) : tail;
    }

    /** 追踪 ID：沿用 MDC（网关/过滤器写入），缺失时生成，保证 Java/Python 日志可串联 */
    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
    }
}

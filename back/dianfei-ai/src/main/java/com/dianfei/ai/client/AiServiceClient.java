package com.dianfei.ai.client;

import com.dianfei.ai.client.dto.AiContracts;

/**
 * AI 旁路服务客户端契约（Sprint 0 冻结，对齐 04 章：业务只经 service 层 + Resilience4j 熔断调用）。
 *
 * <p>调用方约定（D15 降级不阻塞主链路）：
 * <ul>
 *   <li>任何失败（超时/熔断开路/3001/3002）统一抛 {@link AiServiceException}，
 *       编排层捕获后走各能力降级路径（转人工/规则兜底/文本输入），不得向上冒泡阻塞业务</li>
 *   <li>X-Internal-Token 内网鉴权、X-Trace-Id 透传由实现负责，业务无感</li>
 * </ul>
 */
public interface AiServiceClient {

    /** F1 视觉识别 + 阈值决策建议；passThreshold/reviewThreshold 传 null 用服务端默认 */
    AiContracts.VerifyResponse verifyImage(AiContracts.VerifyRequest request,
                                           Double passThreshold, Double reviewThreshold);

    /** F1 3D 模型构建（img2threejs 风格）：视觉 LLM 看真实图片 → spec JSON，前端执行器渲染 */
    AiContracts.Build3dResponse build3d(AiContracts.Build3dRequest request);

    /** CAM 云端精分类（契约 C6-2）：裁剪目标图 → 具体物品名 + 8 类枚举 + 当日报价 */
    AiContracts.ClassifyResponse classifyDetail(AiContracts.ClassifyRequest request);

    /** F2 维修预诊（RAG + LLM，服务端自带知识库规则兜底） */
    AiContracts.DiagnoseResponse repairDiagnose(AiContracts.DiagnoseRequest request);

    /** F7 语音文本 → 意图与槽位 */
    AiContracts.VoiceParseResponse voiceParse(AiContracts.VoiceParseRequest request);

    /** F7 语音转写（ASR）：WAV 16k 单声道 base64 → 文本（本地 vosk 离线推理） */
    AiContracts.VoiceAsrResponse voiceAsr(AiContracts.VoiceAsrRequest request);

    /** F6/YOLO 图片检测 */
    AiContracts.YoloDetectResponse yoloDetect(AiContracts.YoloDetectRequest request);

    /** F9 片区/楼栋回收量预测 */
    AiContracts.ForecastResponse forecast(AiContracts.ForecastRequest request);

    /** F5 动态定价系数建议（服务端已夹逼 0.8-1.2） */
    AiContracts.PricingResponse pricing(AiContracts.PricingRequest request);

    /** 回收市场行情（联网搜索当地当日报价，每日定时同步用） */
    AiContracts.MarketPriceResponse marketPrice(AiContracts.MarketPriceRequest request);

    /** F8 触发一次 Agent 运行（perceive→plan→act/guard→audit），返回决策单草稿 */
    AiContracts.AgentRunResponse agentRun(AiContracts.AgentRunRequest request);
}

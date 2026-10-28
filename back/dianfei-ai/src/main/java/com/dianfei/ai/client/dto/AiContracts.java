package com.dianfei.ai.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 旁路服务出入参契约（Sprint 0 冻结，与 ai-back/app/schemas 逐字段对齐）。
 * Python 侧字段为 snake_case，统一以 {@link JsonNaming} 转换；
 * 响应壳 {@link PyResult} 对齐 schemas.common.Result（HTTP 恒 200，code 承载语义）。
 */
public final class AiContracts {

    private AiContracts() {
    }

    /** Python 侧统一响应壳：code=200 成功 / 3001 AI 不可用（含 fallback 代号）/ 3002 出参校验失败 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PyResult<T> {
        private Integer code;
        private String msg;
        private T data;
        private String traceId;
    }

    // ==================== F1 视觉审核 /v1/verify/image ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VerifyRequest {
        /** RECYCLE / KITCHEN */
        private String bizType;
        private Long bizId;
        private String imageUrl;
        private String claimedCategory;
        private Integer claimedBags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VerifyResponse {
        /** AUTO_PASS / MANUAL_REVIEW / REJECT（阈值决策建议，最终决策 Java 侧落库） */
        private String suggestion;
        private String category;
        private Boolean categoryMatch;
        private Double purity;
        private Double confidence;
        private List<String> violations;
        private String description;
        /** provider/model，写 biz_ai_verify_log.model_name */
        private String modelName;
        private Boolean degraded;
        private Integer elapsedMs;
    }

    // ==================== 3D 构建 /v1/verify/3d-build（img2threejs 风格融合） ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Build3dRequest {
        /** data URL 或内网 HTTP 图片地址 */
        private String imageUrl;
        /** 构建质量模式：standard/advanced/extreme（ai-back 侧非法值按 standard 处理） */
        private String mode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SpecPrimitive {
        /** box / cylinder / sphere / cone / plane */
        private String type;
        private List<Double> size;
        private Double radius;
        private Double height;
        private List<Double> pos;
        private List<Double> rot;
        private String color;
        private Double roughness;
        private Double metalness;
        private Boolean transparent;
        private Double opacity;
        /** none / paper / plastic / metal / glass 材质模拟 */
        private String surface;
        /** box 六面贴图材质（+X,-X,+Y,-Y,+Z,-Z） */
        private List<FaceMaterial> materials;
        /** 侧面环绕标签（cylinder/cone 等） */
        private SideLabel label;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DecalItem {
        /** text / circle / rect / stripe */
        private String type;
        /** 文字内容（OCR 近似） */
        private String text;
        private String color;
        /** 归一化区域 [x,y,w,h]（0-1，左上原点） */
        private List<Double> region;
        /** 文字高度占 region 高度比例 */
        private Double fontSize;
        /** rect 描边模式（条码框/标签框） */
        private Boolean border;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class FaceMaterial {
        /** #rrggbb 或 transparent */
        private String bg;
        private List<DecalItem> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SideLabel {
        /** #rrggbb 或 transparent */
        private String bg;
        private List<DecalItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Build3dSpec {
        /** 物品名（如 牛奶盒） */
        private String item;
        private List<SpecPrimitive> primitives;
        /** 车削体：轮廓点绕 Y 轴（瓶身/杯身曲线） */
        private List<Lathe> lathes = List.of();
        /** 管道：路径点 + 半径（弯曲吸管/把手） */
        private List<Tube> tubes = List.of();
        /** 表面贴花：文字/logo/图标（CanvasTexture 绘制） */
        private List<Decal> decals = List.of();
        /** 整体包围盒比例 [宽,高,深]（高=1） */
        private List<Double> aspect = List.of(1.0, 1.0, 1.0);
        /** none / float / sway / spin 待机动画 */
        private String tick;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Lathe {
        /** 轮廓点 [[r,y],...]（底部到顶部） */
        private List<List<Double>> points;
        private List<Double> pos;
        private List<Double> rot;
        private String color;
        private Double roughness;
        private Double metalness;
        private Boolean transparent;
        private Double opacity;
        /** none / paper / plastic / metal / glass */
        private String surface;
        /** 侧面环绕标签 */
        private SideLabel label;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Tube {
        /** 路径点 [[x,y,z],...] */
        private List<List<Double>> curve;
        private Double radius;
        private List<Double> pos;
        private List<Double> rot;
        private String color;
        private Double roughness;
        private Double metalness;
        private Boolean transparent;
        private Double opacity;
        /** none / paper / plastic / metal / glass */
        private String surface;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Decal {
        /** 文字内容（OCR 近似） */
        private String text;
        /** text / circle / rect / stripe */
        private String shape;
        private String color;
        /** #rrggbb 或 transparent */
        private String bg;
        private List<Double> pos;
        private List<Double> rot;
        private List<Double> size;
        private Double fontSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Build3dResponse {
        /** 为 null = 构建失败，前端回退预设模型库 */
        private Build3dSpec spec;
        private String modelName;
        private Boolean degraded;
        private String error;
        private Integer elapsedMs;
        /** 实际执行模式（extreme 降级 advanced 时回显） */
        private String mode;
    }

    // ==================== F2 维修预诊 /v1/repair/diagnose ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DiagnoseRequest {
        private Long orderId;
        private String deviceType;
        private String brand;
        private String faultDesc;
        private List<String> imageUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DiagnoseResponse {
        private List<FaultCandidate> possibleFaults;
        private Double repairableProbability;
        private List<String> suggestedParts;
        private Double estimatedHours;
        /** ONSITE_REPAIR / STATION_REPAIR / REPLACE_OR_RECYCLE */
        private String suggestion;
        private String reasoning;
        private List<String> knowledgeRefs;
        private String modelName;
        private Boolean degraded;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class FaultCandidate {
        private String fault;
        private Double probability;
    }

    // ==================== F7 语音解析 /v1/voice/parse ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VoiceParseRequest {
        private String sessionId;
        private Long userId;
        private String text;
        /** 多轮上下文 [{role,text}] */
        private List<Map<String, String>> context;
        /** V44 图片扫描升级：可选图片 base64（≤5MB），携带时 ai-back 先做 YOLO 检测 */
        private String imageBase64;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VoiceParseResponse {
        /** BOOK_RECYCLING / QUERY_ORDER / QUERY_CARBON_ACCOUNT / CANCEL_ORDER / SMALL_TALK / UNKNOWN */
        private String intent;
        private VoiceSlots slots;
        private Double confidence;
        private String replyText;
        private List<String> needSlots;
        private String modelName;
        private Boolean degraded;
        /** V44 图片扫描升级：识别后图片（标注检测框的 JPEG base64），未携带图片为 null */
        private String annotatedImageBase64;
        /** V44 图片扫描升级：检测明细（空=未携带图片/无目标） */
        private List<YoloDetectionItem> detections;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VoiceAsrRequest {
        /** WAV 16k 单声道 PCM 音频的 base64（前端 AudioContext 解码重采样生成） */
        private String audioBase64;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VoiceAsrResponse {
        /** 转写文本（空串=未识别到语音） */
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VoiceSlots {
        private String category;
        private String appointTime;
        private String address;
    }

    // ==================== F9 预测 /v1/forecast · F5 定价 /v1/pricing ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ForecastRequest {
        private Long areaId;
        private Long buildingId;
        /** RECYCLE / KITCHEN */
        private String target;
        private Integer horizonHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ForecastResponse {
        private List<ForecastPoint> series;
        private String modelDesc;
        private Integer hotLevel;
        private Boolean degraded;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ForecastPoint {
        private Integer offsetHours;
        private Double predictedKg;
        private Double lower;
        private Double upper;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PricingRequest {
        private Long areaId;
        private String category;
        private Double basePrice;
        private Double recentSupplyKg;
        private Double recentDemandKg;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PricingResponse {
        private Double priceFactor;
        private Double pointsFactor;
        private String reason;
        private Integer validHours;
        private Boolean degraded;
    }

    // ==================== 回收市场行情 /v1/market-price（每日联网报价同步） ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MarketPriceRequest {
        /** 回收品类（biz_recycle_price.category） */
        private String category;
        /** 城市（当地行情） */
        private String city;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MarketPriceResponse {
        private String category;
        /** 当日报价（元/公斤）；null=未检索到，保留原价 */
        private Double pricePerKg;
        private String unit;
        private String note;
        private String source;
        /** 降级标记（正则/mock 提取，非 LLM 提炼；Java 侧可按配置丢弃） */
        private Boolean degraded;
    }

    // ==================== F8 多智能体 /v1/agent/run ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AgentRunRequest {
        /** AGENT-01 调度 / AGENT-02 风控 / AGENT-03 客服 / AGENT-04 复盘 */
        private String agentType;
        private List<AgentEvent> events;
        /** true=只产出决策单不回调平台接口 */
        private Boolean dryRun;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AgentEvent {
        /** PENDING_ORDER / RISK_ALERT / USER_TICKET / DAILY_METRICS */
        private String eventType;
        private Map<String, Object> payload;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AgentRunResponse {
        private String agentType;
        private List<DecisionDraft> decisions;
        private String summary;
        private String modelName;
        private Boolean degraded;
    }

    /** 决策单草稿：Java 侧落库 biz_agent_decision（含哈希链） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DecisionDraft {
        private String agentType;
        private String action;
        private String targetUri;
        private Map<String, Object> payloadJson;
        /** LOW / MEDIUM / HIGH */
        private String riskLevel;
        /** [{source: RULE|MODEL|DATA_QUERY, description, data_ref}] */
        private List<Map<String, String>> evidences;
        private Boolean executed;
        private String executeResult;
    }

    // ==================== CAM 精分类 /v1/classify/detail（契约 C6-2） ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ClassifyRequest {
        /** base64 裁剪图 (≤300KB, 契约 C6-3) */
        private String imageUrl;
        /** 端侧大类 code,可空 (paper/plastic/...) */
        private String categoryHint;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ClassifyResponse {
        /** 具体物品名（≤20 字），识别失败为空串 */
        private String itemName;
        /** 8 类中文枚举之一（契约 C6-6） */
        private String category;
        private Double confidence;
        private Double purity;
        /** 投放建议 */
        private String advice;
        /** 当日报价 元/kg（厨余/未知为 null，前端用 RECYCLE_CATEGORIES 兜底） */
        private Double pricePerKg;
        /** 链路降级标记 */
        private Boolean degraded;
        private Integer elapsedMs;
        /** provider/model，降级时为空 */
        private String modelName;
    }

    // ==================== F6/YOLO /v1/yolo/detect (V44 F7 升级新增) ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class YoloDetectRequest {
        private String imageBase64;
        private Double scoreThreshold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class YoloDetectionItem {
        private String className;
        private Double confidence;
        private int[] bbox;
        private double[] center;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class YoloDetectResponse {
        private List<YoloDetectionItem> detections;
        private Integer imageWidth;
        private Integer imageHeight;
        private String annotatedImageBase64;
        private String modelName;
        private Boolean degraded;
    }
}

package com.dianfei.business.repair.diagnose;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * F2 维修智能预诊出入参（Sprint 0 冻结 Schema，对齐 12.2 POST /api/ai/repair/diagnose）
 */
public class RepairDiagnoseContract {

    private RepairDiagnoseContract() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        /** 故障类型（字典驱动） */
        private String faultType;
        /** 用户故障描述（文字或 F7 语音转写） */
        private String faultDesc;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** 可能故障列表（按置信度降序） */
        private List<FaultGuess> possibleFaults;
        /** 建议携带配件 */
        private List<String> suggestParts;
        /** 可修复概率 0-1 */
        private Double repairableProbability;
        /** 分流建议：REPAIR 走维修 / RECYCLE 引导转回收 */
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaultGuess {
        private String fault;
        private Double confidence;
    }
}

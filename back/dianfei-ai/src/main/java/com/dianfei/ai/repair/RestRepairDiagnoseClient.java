package com.dianfei.ai.repair;

import cn.hutool.core.util.StrUtil;
import com.dianfei.ai.client.AiServiceClient;
import com.dianfei.ai.client.dto.AiContracts;
import com.dianfei.business.repair.diagnose.RepairDiagnoseClient;
import com.dianfei.business.repair.diagnose.RepairDiagnoseContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * F2 预诊真实客户端（E7）：适配 Track C 冻结的 {@link RepairDiagnoseClient} SPI，
 * 转调 AI 旁路服务 /v1/repair/diagnose（RAG 关键词知识库 + LLM，Python 侧带 KNOWLEDGE_RULE 兜底）。
 *
 * <p>@Primary 覆盖 {@code MockRepairDiagnoseClient} 占位；AI 不可用时异常原样上抛，
 * 由 {@code RepairOrderService.tryDiagnose} 捕获降级为自由备注（D15，不阻塞下单主链路）。
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RestRepairDiagnoseClient implements RepairDiagnoseClient {

    private final AiServiceClient aiServiceClient;

    @Override
    public RepairDiagnoseContract.Response diagnose(RepairDiagnoseContract.Request request) {
        AiContracts.DiagnoseResponse resp = aiServiceClient.repairDiagnose(
                AiContracts.DiagnoseRequest.builder()
                        .deviceType(StrUtil.blankToDefault(request.getFaultType(), "小家电"))
                        .faultDesc(StrUtil.blankToDefault(request.getFaultDesc(), "未描述故障"))
                        .build());
        return toContract(resp);
    }

    /** AI 契约 → Track C 冻结契约：REPLACE_OR_RECYCLE 归并为 RECYCLE 引导转回收 */
    static RepairDiagnoseContract.Response toContract(AiContracts.DiagnoseResponse resp) {
        List<RepairDiagnoseContract.FaultGuess> faults = resp.getPossibleFaults() == null
                ? List.of()
                : resp.getPossibleFaults().stream()
                        .map(f -> RepairDiagnoseContract.FaultGuess.builder()
                                .fault(f.getFault())
                                .confidence(f.getProbability())
                                .build())
                        .toList();
        return RepairDiagnoseContract.Response.builder()
                .possibleFaults(faults)
                .suggestParts(resp.getSuggestedParts() == null ? List.of() : resp.getSuggestedParts())
                .repairableProbability(resp.getRepairableProbability())
                .suggestion("REPLACE_OR_RECYCLE".equals(resp.getSuggestion()) ? "RECYCLE" : "REPAIR")
                .build();
    }
}

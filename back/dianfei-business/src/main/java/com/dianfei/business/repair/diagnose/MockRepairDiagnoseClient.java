package com.dianfei.business.repair.diagnose;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * F2 预诊 Mock 实现（Track E 未就绪期间占位，就绪后删除切换真实 REST 客户端）：
 * 按故障类型/描述关键词返回固定建议，规则可演示"概率高走维修、概率低引导转回收"两条分支。
 */
@Slf4j
@Component
public class MockRepairDiagnoseClient implements RepairDiagnoseClient {

    @Override
    public RepairDiagnoseContract.Response diagnose(RepairDiagnoseContract.Request request) {
        String text = ((request.getFaultType() == null ? "" : request.getFaultType())
                + (request.getFaultDesc() == null ? "" : request.getFaultDesc()))
                .toLowerCase(Locale.ROOT);
        log.info("[MockRepairDiagnose] faultType={} descLen={}",
                request.getFaultType(), request.getFaultDesc() == null ? 0 : request.getFaultDesc().length());

        // 老旧插排/烧毁类：可修复概率低，引导转回收（对齐 C2 强制联动链路）
        if (text.contains("插排") || text.contains("烧") || text.contains("老化") || text.contains("冒烟")) {
            return RepairDiagnoseContract.Response.builder()
                    .possibleFaults(List.of(
                            guess("内部线路老化短路", 0.82),
                            guess("插座簧片烧蚀", 0.61)))
                    .suggestParts(List.of())
                    .repairableProbability(0.15)
                    .suggestion("RECYCLE")
                    .build();
        }
        if (text.contains("灯") || text.contains("照明")) {
            return RepairDiagnoseContract.Response.builder()
                    .possibleFaults(List.of(
                            guess("镇流器/驱动电源故障", 0.74),
                            guess("灯管/灯珠损坏", 0.68)))
                    .suggestParts(List.of("LED 驱动电源", "灯管"))
                    .repairableProbability(0.9)
                    .suggestion("REPAIR")
                    .build();
        }
        // 默认：小家电通用预诊
        return RepairDiagnoseContract.Response.builder()
                .possibleFaults(List.of(
                        guess("电源模块故障", 0.55),
                        guess("保险丝熔断", 0.4)))
                .suggestParts(List.of("通用保险丝", "万用表"))
                .repairableProbability(0.7)
                .suggestion("REPAIR")
                .build();
    }

    private RepairDiagnoseContract.FaultGuess guess(String fault, double confidence) {
        return RepairDiagnoseContract.FaultGuess.builder()
                .fault(fault)
                .confidence(confidence)
                .build();
    }
}

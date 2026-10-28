package com.dianfei.business.repair.diagnose;

/**
 * F2 预诊客户端接口：Track E dianfei-ai 就绪后替换为真实 REST 调用（Resilience4j 熔断）。
 * 调用方约定：任何异常均降级为 null（用户自由备注下单），不阻塞主链路（D15）。
 */
public interface RepairDiagnoseClient {

    RepairDiagnoseContract.Response diagnose(RepairDiagnoseContract.Request request);
}

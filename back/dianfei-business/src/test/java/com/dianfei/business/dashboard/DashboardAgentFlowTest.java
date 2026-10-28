package com.dianfei.business.dashboard;

import com.dianfei.business.agent.dto.DecisionSubmitDTO;
import com.dianfei.business.agent.entity.AgentDecision;
import com.dianfei.business.agent.service.AgentDecisionService;
import com.dianfei.business.dashboard.service.DashboardExportService;
import com.dianfei.business.dashboard.service.DashboardService;
import com.dianfei.business.dashboard.vo.AreaRankVO;
import com.dianfei.business.dashboard.vo.DashboardOverviewVO;
import com.dianfei.business.dashboard.vo.MonthlyReportVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.HashChainUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * C5 大屏 + Agent 驾驶舱闭环单测（H2）：
 * 聚合口径增量校验 / 1s 快照缓存 / 台账月报导出 /
 * 低风险自动执行 / 中风险单人审批 / 高风险双人复核（同人拦截）/ 驳回留痕 / 哈希链健康
 */
@SpringBootTest
class DashboardAgentFlowTest {

    private static final AtomicLong ID_SEQ = new AtomicLong(700_000L);

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private DashboardExportService exportService;
    @Autowired
    private AgentDecisionService agentDecisionService;
    @Autowired
    private JdbcTemplate jdbc;

    // ---------- 大屏聚合 ----------

    @Test
    void overviewAggregatesSeededData() {
        DashboardOverviewVO before = dashboardService.refresh();

        long areaId = ID_SEQ.incrementAndGet();
        jdbc.update("INSERT INTO biz_area(id, area_name, clean_threshold, household_count, status, deleted) "
                + "VALUES (?, ?, 100, 50, 1, 0)", areaId, "大屏测试片区" + areaId);
        LocalDateTime now = LocalDateTime.now();
        // 完成回收单 12.5kg + 今日待接单 1 单
        insertRecycleOrder(areaId, 3, new BigDecimal("12.50"), now);
        insertRecycleOrder(areaId, 0, null, now);
        // 厨余 3 袋（今日）
        jdbc.update("INSERT INTO biz_kitchen_report(id, user_id, area_id, point_name, bag_count, report_time, status, deleted) "
                        + "VALUES (?, ?, ?, '1号点', 3, ?, 0, 0)",
                ID_SEQ.incrementAndGet(), ID_SEQ.incrementAndGet(), areaId, now);
        // 维修：1 完成 + 1 转回收
        insertRepairOrder(areaId, 3, now);
        insertRepairOrder(areaId, 5, now);
        // 碳账本账户累计减排 4.2 kgCO2
        jdbc.update("INSERT INTO biz_carbon_account(id, user_id, points_balance, frozen_points, total_carbon_reduced, deleted) "
                        + "VALUES (?, ?, 0, 0, 4.2000, 0)",
                ID_SEQ.incrementAndGet(), ID_SEQ.incrementAndGet());

        DashboardOverviewVO after = dashboardService.refresh();

        assertThat(after.getRecycleTotalOrders() - before.getRecycleTotalOrders()).isEqualTo(2);
        assertThat(after.getRecycleFinishedOrders() - before.getRecycleFinishedOrders()).isEqualTo(1);
        assertThat(after.getRecycleTotalWeight().subtract(before.getRecycleTotalWeight()))
                .isEqualByComparingTo("12.50");
        assertThat(after.getRecycleTodayOrders() - before.getRecycleTodayOrders()).isEqualTo(2);
        assertThat(after.getKitchenTotalBags() - before.getKitchenTotalBags()).isEqualTo(3);
        assertThat(after.getKitchenTodayBags() - before.getKitchenTodayBags()).isEqualTo(3);
        assertThat(after.getRepairTotalOrders() - before.getRepairTotalOrders()).isEqualTo(2);
        assertThat(after.getRepairFinishedOrders() - before.getRepairFinishedOrders()).isEqualTo(1);
        assertThat(after.getRepairTransferredOrders() - before.getRepairTransferredOrders()).isEqualTo(1);
        assertThat(after.getCarbonReducedTotal().subtract(before.getCarbonReducedTotal()))
                .isCloseTo(new BigDecimal("4.2"), within(new BigDecimal("0.0001")));

        // 片区排行含本片区且口径正确
        AreaRankVO rank = after.getAreaRanking().stream()
                .filter(r -> r.getAreaId().equals(areaId)).findFirst().orElseThrow();
        assertThat(rank.getRecycleOrders()).isEqualTo(1);
        assertThat(rank.getRecycleWeight()).isEqualByComparingTo("12.50");
        assertThat(rank.getKitchenBags()).isEqualTo(3);

        // 月度报表：12 行连续、当月行含本次增量
        assertThat(after.getMonthlyReport()).hasSize(12);
        String currentMonth = YearMonth.now().toString();
        MonthlyReportVO beforeRow = before.getMonthlyReport().stream()
                .filter(r -> r.getMonth().equals(currentMonth)).findFirst().orElseThrow();
        MonthlyReportVO afterRow = after.getMonthlyReport().stream()
                .filter(r -> r.getMonth().equals(currentMonth)).findFirst().orElseThrow();
        assertThat(afterRow.getRecycleOrders() - beforeRow.getRecycleOrders()).isEqualTo(1);
        assertThat(afterRow.getRecycleWeight().subtract(beforeRow.getRecycleWeight()))
                .isEqualByComparingTo("12.50");
        assertThat(afterRow.getKitchenBags() - beforeRow.getKitchenBags()).isEqualTo(3);
        assertThat(afterRow.getRepairOrders() - beforeRow.getRepairOrders()).isEqualTo(2);
    }

    @Test
    void overviewSnapshotCachedWithinOneSecond() {
        DashboardOverviewVO first = dashboardService.refresh();
        DashboardOverviewVO second = dashboardService.overview();
        // 1s 窗口内命中同一快照（不重复聚合打库）
        assertThat(second).isSameAs(first);
        assertThat(dashboardService.refresh()).isNotSameAs(first);
    }

    @Test
    void ledgerExportProducesWorkbook() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exportLedger(out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(0);
        // xlsx = zip 容器，魔数 PK
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    // ---------- Agent 驾驶舱（HITL） ----------

    @Test
    void lowRiskAutoExecuted() {
        AgentDecision decision = agentDecisionService.submit(submitDTO("dispatch-agent", AgentDecision.RISK_LOW));
        assertThat(decision.getStatus()).isEqualTo(AgentDecision.STATUS_EXECUTED);
        assertThat(decision.getExecuteResult()).startsWith("MOCK_EXECUTED:");
        assertThat(decision.getDecisionHash()).hasSize(64);
        // 低风险单不允许再人工审批
        assertThatThrownBy(() -> agentDecisionService.approve(decision.getId(), true, null, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void mediumRiskSingleApprovalThenExecute() {
        AgentDecision decision = agentDecisionService.submit(submitDTO("risk-agent", AgentDecision.RISK_MEDIUM));
        assertThat(decision.getStatus()).isEqualTo(AgentDecision.STATUS_PENDING);

        AgentDecision approved = agentDecisionService.approve(decision.getId(), true, null, 1001L);
        assertThat(approved.getStatus()).isEqualTo(AgentDecision.STATUS_EXECUTED);
        assertThat(approved.getFirstApprover()).isEqualTo(1001L);
        assertThat(approved.getExecuteResult()).startsWith("MOCK_EXECUTED:");
        // 已终结不允许重复审批
        assertThatThrownBy(() -> agentDecisionService.approve(decision.getId(), true, null, 1002L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void highRiskRequiresTwoDistinctApprovers() {
        AgentDecision decision = agentDecisionService.submit(submitDTO("risk-agent", AgentDecision.RISK_HIGH));

        AgentDecision afterFirst = agentDecisionService.approve(decision.getId(), true, null, 2001L);
        assertThat(afterFirst.getStatus()).isEqualTo(AgentDecision.STATUS_PENDING);
        assertThat(afterFirst.getFirstApprover()).isEqualTo(2001L);

        // 同一人二审被拦截（双人复核）
        assertThatThrownBy(() -> agentDecisionService.approve(decision.getId(), true, null, 2001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getResultCode())
                        .isEqualTo(ResultCode.FORBIDDEN));

        AgentDecision executed = agentDecisionService.approve(decision.getId(), true, null, 2002L);
        assertThat(executed.getStatus()).isEqualTo(AgentDecision.STATUS_EXECUTED);
        assertThat(executed.getSecondApprover()).isEqualTo(2002L);
    }

    @Test
    void rejectRequiresReasonAndWritesBack() {
        AgentDecision decision = agentDecisionService.submit(submitDTO("service-agent", AgentDecision.RISK_MEDIUM));

        assertThatThrownBy(() -> agentDecisionService.approve(decision.getId(), false, " ", 3001L))
                .isInstanceOf(BusinessException.class);

        AgentDecision rejected = agentDecisionService.approve(decision.getId(), false, "证据不足，驳回", 3001L);
        assertThat(rejected.getStatus()).isEqualTo(AgentDecision.STATUS_REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo("证据不足，驳回");
        assertThat(rejected.getFirstApprover()).isEqualTo(3001L);
    }

    @Test
    void cockpitChainStaysValid() {
        AgentDecision first = agentDecisionService.submit(submitDTO("review-agent", AgentDecision.RISK_LOW));
        AgentDecision second = agentDecisionService.submit(submitDTO("review-agent", AgentDecision.RISK_MEDIUM));
        // 链式连接：后一条 prev = 前一条 hash
        assertThat(second.getPrevHash()).isEqualTo(first.getDecisionHash());
        assertThat(HashChainUtil.verify(first.getPrevHash(), payloadOf(first), first.getDecisionHash())).isTrue();

        var cockpit = agentDecisionService.cockpit();
        assertThat(cockpit.getChainValid()).isTrue();
        assertThat(cockpit.getChainLength()).isGreaterThanOrEqualTo(2);
        assertThat(cockpit.getAgents()).extracting("agentId").contains("review-agent");
        assertThat(cockpit.getPendingByRisk().getOrDefault(AgentDecision.RISK_MEDIUM, 0L)).isGreaterThanOrEqualTo(1);
    }

    // ---------- helpers ----------

    private DecisionSubmitDTO submitDTO(String agentId, int riskLevel) {
        DecisionSubmitDTO dto = new DecisionSubmitDTO();
        dto.setAgentId(agentId);
        dto.setActionType("POINTS_ADJUST");
        dto.setTargetUri("/api/admin/carbon/adjust");
        dto.setParamsJson("{\"userId\":1,\"points\":-50}");
        dto.setEvidencesJson("[{\"source\":\"RULE\",\"description\":\"3 日上报超均值 5 倍\",\"dataRef\":\"biz_kitchen_report\"}]");
        dto.setRiskLevel(riskLevel);
        return dto;
    }

    private static java.util.Map<String, Object> payloadOf(AgentDecision d) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("agentId", d.getAgentId());
        payload.put("actionType", d.getActionType());
        payload.put("targetUri", d.getTargetUri());
        payload.put("paramsJson", d.getParamsJson());
        payload.put("evidencesJson", d.getEvidencesJson());
        payload.put("riskLevel", d.getRiskLevel());
        return payload;
    }

    private void insertRecycleOrder(long areaId, int status, BigDecimal weight, LocalDateTime now) {
        long id = ID_SEQ.incrementAndGet();
        jdbc.update("INSERT INTO biz_recycle_order(id, order_no, user_id, area_id, category, weight, address, "
                        + "appoint_time, status, finish_time, create_time, deleted) "
                        + "VALUES (?, ?, ?, ?, '纸类', ?, '测试地址', ?, ?, ?, ?, 0)",
                id, "RO" + id, ID_SEQ.incrementAndGet(), areaId, weight, now, status,
                status == 3 ? now : null, now);
    }

    private void insertRepairOrder(long areaId, int status, LocalDateTime now) {
        long id = ID_SEQ.incrementAndGet();
        jdbc.update("INSERT INTO biz_repair_order(id, order_no, user_id, area_id, fault_type, address, "
                        + "appoint_start, appoint_end, status, create_time, deleted) "
                        + "VALUES (?, ?, ?, ?, '插排', '测试地址', ?, ?, ?, ?, 0)",
                id, "RP" + id, ID_SEQ.incrementAndGet(), areaId, now, now.plusHours(2), status, now);
    }
}

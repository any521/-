package com.dianfei.ai.agent.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.ai.agent.dto.AgentRunDTO;
import com.dianfei.ai.agent.service.AgentRunService;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.common.enums.OrderStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * F8 Agent 巡检触发器（06 章 perceive 事件源）：
 * 调度 Agent 每 30 分钟巡检当日滞留待派单；复盘 Agent 每日 08:00 生成前日经营复盘。
 * 风控/客服 Agent 由业务事件（B4 风控告警、工单）按需触发，不在定时器内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPatrolTask {

    private final AgentRunService agentRunService;
    private final RecycleOrderMapper recycleOrderMapper;

    /** AGENT-01 调度巡检：当日存在未指派待接单 → 触发 REDISPATCH 决策（LOW 自动执行） */
    @Scheduled(cron = "0 */30 * * * ?")
    public void patrolPendingOrders() {
        try {
            LocalDate today = LocalDate.now();
            Long stranded = recycleOrderMapper.selectCount(new LambdaQueryWrapper<RecycleOrder>()
                    .eq(RecycleOrder::getStatus, OrderStatusEnum.PENDING.getCode())
                    .isNull(RecycleOrder::getStaffId)
                    .ge(RecycleOrder::getAppointTime, today.atStartOfDay())
                    .lt(RecycleOrder::getAppointTime, today.plusDays(1).atStartOfDay()));
            if (stranded == null || stranded == 0) {
                return;
            }
            AgentRunDTO dto = new AgentRunDTO();
            dto.setAgentType("AGENT-01");
            AgentRunDTO.Event event = new AgentRunDTO.Event();
            event.setEventType("PENDING_ORDER");
            event.setPayload(Map.of("date", today.toString(), "stranded_count", stranded));
            dto.setEvents(List.of(event));
            agentRunService.run(dto);
            log.info("[F8] 调度巡检：{} 滞留 {} 单，已触发 AGENT-01", today, stranded);
        } catch (Exception e) {
            // 巡检失败只记日志，不影响主链路（D15）
            log.warn("[F8] 调度巡检异常：{}", e.getMessage());
        }
    }

    /** AGENT-04 每日复盘：聚合前日指标生成日报决策（NOTIFY，LOW 自动执行） */
    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyReview() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            Long finished = recycleOrderMapper.selectCount(new LambdaQueryWrapper<RecycleOrder>()
                    .eq(RecycleOrder::getStatus, OrderStatusEnum.COMPLETED.getCode())
                    .ge(RecycleOrder::getUpdateTime, yesterday.atStartOfDay())
                    .lt(RecycleOrder::getUpdateTime, yesterday.plusDays(1).atStartOfDay()));
            AgentRunDTO dto = new AgentRunDTO();
            dto.setAgentType("AGENT-04");
            AgentRunDTO.Event event = new AgentRunDTO.Event();
            event.setEventType("DAILY_METRICS");
            event.setPayload(Map.of("date", yesterday.toString(),
                    "finished_recycle_orders", finished == null ? 0 : finished));
            dto.setEvents(List.of(event));
            agentRunService.run(dto);
            log.info("[F8] 复盘巡检：{} 完成单 {}，已触发 AGENT-04", yesterday, finished);
        } catch (Exception e) {
            log.warn("[F8] 复盘巡检异常：{}", e.getMessage());
        }
    }
}

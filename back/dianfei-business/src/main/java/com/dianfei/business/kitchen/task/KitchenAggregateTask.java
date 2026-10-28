package com.dianfei.business.kitchen.task;

import com.dianfei.business.kitchen.service.KitchenTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 垃圾投放聚合定时任务（B4）：每晚 23:10（投放窗口关闭后）汇总当日袋数，
 * 达片区阈值生成次日清运任务与派车建议；uk_task_area_date 幂等，失败可重跑或后台手动触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenAggregateTask {

    private final KitchenTaskService taskService;

    @Scheduled(cron = "0 10 23 * * ?")
    public void aggregateToday() {
        LocalDate today = LocalDate.now();
        try {
            taskService.aggregate(today);
        } catch (Exception e) {
            log.error("[Kitchen] {} 聚合任务失败", today, e);
        }
    }
}

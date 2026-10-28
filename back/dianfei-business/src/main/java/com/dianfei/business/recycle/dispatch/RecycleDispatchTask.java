package com.dianfei.business.recycle.dispatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 派单定时任务（B3）：每日 07:00 对当日预约单做片区聚合派单；
 * 失败不影响下单主链路，滞留单由下一轮任务或后台手动改派兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleDispatchTask {

    private final RecycleDispatchService dispatchService;

    @Scheduled(cron = "0 0 7 * * ?")
    public void dispatchToday() {
        LocalDate today = LocalDate.now();
        try {
            dispatchService.dispatchDate(today);
        } catch (Exception e) {
            log.error("[Dispatch] {} 派单任务失败", today, e);
        }
    }
}

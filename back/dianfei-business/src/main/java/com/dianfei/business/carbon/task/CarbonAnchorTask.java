package com.dianfei.business.carbon.task;

import com.dianfei.business.carbon.service.CarbonAnchorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日锚定定时任务（B2）：每天 00:10 对前一日流水做 Merkle 锚定
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarbonAnchorTask {

    private final CarbonAnchorService anchorService;

    @Scheduled(cron = "0 10 0 * * ?")
    public void anchorYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            anchorService.anchorFor(yesterday);
        } catch (Exception e) {
            // 失败不影响业务写账，次日任务或手动触发可补锚
            log.error("[CarbonAnchor] {} 锚定失败", yesterday, e);
        }
    }
}

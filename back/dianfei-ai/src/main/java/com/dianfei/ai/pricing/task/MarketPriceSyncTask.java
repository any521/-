package com.dianfei.ai.pricing.task;

import com.dianfei.ai.pricing.service.MarketPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回收报价每日联网同步触发器（B3 实时报价）：每日 08:05 调 {@link MarketPriceService#syncAll()}
 * （博查/Tavily 联网搜索当地行情 + LLM 提炼，先 IP 定位城市）。用户端实时报价页另有
 * 手动「联网更新」按钮复用同一逻辑；护栏与开关见 MarketPriceService 类注释。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceSyncTask {

    private final MarketPriceService marketPriceService;

    /** 每日 08:05（避让 07:00 派单巡检高峰）；与手动同步共用 Redis 互斥锁，
     * 手动同步进行中时本日自动跳过（锁被占用，syncAsync 返回 started=false 静默） */
    @Scheduled(cron = "0 5 8 * * ?")
    public void syncDaily() {
        if (!"1".equals(marketPriceService.syncEnabled())) {
            log.info("[行情] 每日报价同步已关闭（recycle.price.sync.enabled=0）");
            return;
        }
        marketPriceService.syncAsync(null);
    }
}

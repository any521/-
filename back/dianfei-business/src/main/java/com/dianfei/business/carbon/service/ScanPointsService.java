package com.dianfei.business.carbon.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.dto.ScanPointsDTO;
import com.dianfei.business.carbon.vo.ScanPointsVO;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 扫描识别积分（CAM cam04，契约 C6-9）：
 * sys_config 配置单次额度与日上限（carbon.scan.points / carbon.scan.daily_limit），
 * Redis 日计数防刷（KEY carbon:scan:points:{userId}:{yyyyMMdd}，次日 0 点过期），
 * 未超限走账本唯一发放入口 {@link CarbonLedgerOps#grantScan}（rule_code=SCAN_CLASSIFY）。
 * 超限返回 hitLimit=true + points=0，不报错不阻断（对齐 C6-5 静默降级）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanPointsService {

    private static final String CFG_POINTS = "carbon.scan.points";
    private static final String CFG_DAILY_LIMIT = "carbon.scan.daily_limit";
    private static final String REDIS_KEY = "carbon:scan:points:%s:%s";
    private static final String DEFAULT_POINTS = "1";
    private static final String DEFAULT_DAILY_LIMIT = "10";

    private final ConfigService configService;
    private final RedisUtils redisUtils;
    private final CarbonLedgerOps carbonLedger;

    public ScanPointsVO award(Long userId, ScanPointsDTO dto) {
        long dailyLimit = Long.parseLong(configService.getValue(CFG_DAILY_LIMIT, DEFAULT_DAILY_LIMIT));
        String dayKey = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        // 日计数：INCR 后判断，超限的本次也计数（防刷口径：每日实际识别次数）
        long dailyTotal = redisUtils.increment(REDIS_KEY.formatted(userId, dayKey), untilMidnight());
        if (dailyTotal > dailyLimit) {
            log.info("[ScanPoints] daily limit hit userId={} total={} limit={}", userId, dailyTotal, dailyLimit);
            return ScanPointsVO.builder()
                    .points(0).dailyTotal(dailyTotal).dailyLimit(dailyLimit).hitLimit(true).build();
        }
        long points = Long.parseLong(configService.getValue(CFG_POINTS, DEFAULT_POINTS));
        CarbonLedgerResult result = carbonLedger.grantScan(userId, IdUtil.getSnowflakeNextId(), points,
                remark(dto));
        log.info("[ScanPoints] awarded userId={} points={} total={} limit={} logId={}",
                userId, result.getPoints(), dailyTotal, dailyLimit, result.getLogId());
        return ScanPointsVO.builder()
                .points(result.getPoints()).dailyTotal(dailyTotal).dailyLimit(dailyLimit).hitLimit(false).build();
    }

    private static String remark(ScanPointsDTO dto) {
        if (dto == null || (StrUtil.isBlank(dto.getCategory()) && StrUtil.isBlank(dto.getItemName()))) {
            return "扫描识别积分";
        }
        return StrUtil.format("扫描识别积分：{}·{}", StrUtil.nullToDefault(dto.getCategory(), "未知品类"),
                StrUtil.nullToDefault(dto.getItemName(), "未命名物品"));
    }

    /** 到当日 24:00 的存活时长（次日 0 点自动清零重计） */
    private static Duration untilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.ofSeconds(Math.max(ChronoUnit.SECONDS.between(now, midnight), 1));
    }
}

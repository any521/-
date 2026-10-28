package com.dianfei.business.eco.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.eco.entity.EcoAchievement;
import com.dianfei.business.eco.entity.EcoSignIn;
import com.dianfei.business.eco.entity.EcoUserAchievement;
import com.dianfei.business.eco.mapper.EcoAchievementMapper;
import com.dianfei.business.eco.mapper.EcoSignInMapper;
import com.dianfei.business.eco.mapper.EcoUserAchievementMapper;
import com.dianfei.business.eco.vo.EcoAchievementVO;
import com.dianfei.business.eco.vo.EcoClaimVO;
import com.dianfei.business.eco.vo.EcoSignResultVO;
import com.dianfei.business.eco.vo.EcoSignStatusVO;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.mapper.KitchenReportMapper;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 环保成就 · 每日签到（LoopFill B5 创意 C1，契约 C5）：
 * 签到每日一次 +5 积分（sys_config eco.sign-in-points 热更，uk 防重复），
 * 积分一律走账本唯一入口 {@link CarbonLedgerOps#grantEco}（幂等键 ECO:{bizId}:GAIN）；
 * 连续签到 ≥ eco.sign-streak-days（默认 7）自动解锁 SIGN_SEVEN（claimed=0 待领取）；
 * 成就达成判定复用业务表（回收完成单 status=3、厨余本人上报数、签到连续天数）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcoTaskService {

    private static final String CFG_SIGN_POINTS = "eco.sign-in-points";
    private static final String CFG_STREAK_DAYS = "eco.sign-streak-days";
    private static final String DEFAULT_SIGN_POINTS = "5";
    private static final String DEFAULT_STREAK_DAYS = "7";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String CODE_RECYCLE_FIRST = "RECYCLE_FIRST";
    public static final String CODE_KITCHEN_FIRST = "KITCHEN_FIRST";
    public static final String CODE_RECYCLE_TEN = "RECYCLE_TEN";
    public static final String CODE_SIGN_SEVEN = "SIGN_SEVEN";

    private final EcoSignInMapper signInMapper;
    private final EcoAchievementMapper achievementMapper;
    private final EcoUserAchievementMapper userAchievementMapper;
    private final RecycleOrderMapper recycleOrderMapper;
    private final KitchenReportMapper kitchenReportMapper;
    private final CarbonLedgerOps carbonLedger;
    private final ConfigService configService;

    /** 每日签到：未签插入 eco_sign_in + 账本入账；连续 ≥ 阈值自动解锁 SIGN_SEVEN */
    @Transactional
    public EcoSignResultVO signIn(Long userId) {
        LocalDate today = LocalDate.now();
        if (signInMapper.selectCount(new LambdaQueryWrapper<EcoSignIn>()
                .eq(EcoSignIn::getUserId, userId).eq(EcoSignIn::getSignDate, today)) > 0) {
            throw new BusinessException("今日已签到");
        }
        int signPoints = signPoints();
        EcoSignIn row = new EcoSignIn();
        row.setUserId(userId);
        row.setSignDate(today);
        row.setPoints(signPoints);
        try {
            signInMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发重复签到：uk(user_id, sign_date) 兜底，同「今日已签到」
            log.info("[Eco] duplicate sign-in userId={} date={}", userId, today);
            throw new BusinessException("今日已签到");
        }
        carbonLedger.grantEco(userId, row.getId(), signPoints, "ECO_SIGN",
                "环保签到 " + today.format(DATE) + " +" + signPoints + " 分");
        if (streakDays(userId) >= streakDaysRequired()) {
            unlock(userId, CODE_SIGN_SEVEN);
        }
        log.info("[Eco] sign-in userId={} date={} points={} logId={}", userId, today, signPoints, row.getId());
        return EcoSignResultVO.builder().points(signPoints).streakDays(streakDays(userId)).build();
    }

    /** 签到状态：本月日历 + 连续天数 + 累计 + 单次积分 */
    public EcoSignStatusVO signStatus(Long userId) {
        Set<LocalDate> signed = new HashSet<>(signDates(userId));
        YearMonth ym = YearMonth.now();
        List<EcoSignStatusVO.MonthDay> monthDays = new ArrayList<>(ym.lengthOfMonth());
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            monthDays.add(EcoSignStatusVO.MonthDay.builder()
                    .date(date.format(DATE)).signed(signed.contains(date)).build());
        }
        return EcoSignStatusVO.builder()
                .monthDays(monthDays)
                .streakDays(streakDays(userId))
                .totalSigns(signed.size())
                .signPoints(signPoints())
                .build();
    }

    /** 成就全量 + 达成/领取态（unlocked 兜底判定，曾解锁过的保持 true） */
    public List<EcoAchievementVO> achievements(Long userId) {
        List<EcoAchievement> defs = achievementMapper.selectList(
                new LambdaQueryWrapper<EcoAchievement>().orderByAsc(EcoAchievement::getSort));
        Map<Long, EcoUserAchievement> mine = userAchievementMapper.selectList(
                        new LambdaQueryWrapper<EcoUserAchievement>().eq(EcoUserAchievement::getUserId, userId))
                .stream().collect(Collectors.toMap(EcoUserAchievement::getAchievementId, Function.identity(), (a, b) -> a));
        long completedRecycle = recycleOrderMapper.selectCount(new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getUserId, userId)
                .eq(RecycleOrder::getStatus, OrderStatusEnum.COMPLETED.getCode()));
        boolean kitchenFirst = kitchenReportMapper.selectCount(
                new LambdaQueryWrapper<KitchenReport>().eq(KitchenReport::getUserId, userId)) > 0;
        int maxStreak = maxStreak(signDates(userId));
        return defs.stream().map(def -> {
            boolean unlocked = mine.containsKey(def.getId())
                    || unlocked(def.getCode(), completedRecycle, kitchenFirst, maxStreak);
            boolean claimed = mine.containsKey(def.getId()) && mine.get(def.getId()).getClaimed() == 1;
            return EcoAchievementVO.builder()
                    .code(def.getCode()).name(def.getName()).description(def.getDescription())
                    .points(def.getPoints()).unlocked(unlocked).claimed(claimed)
                    .build();
        }).toList();
    }

    /** 领取成就奖励：未达成抛错；已领取幂等返回；未领取 → claimed=1 + 账本入账 */
    @Transactional
    public EcoClaimVO claim(Long userId, String code) {
        EcoAchievement def = achievementMapper.selectOne(
                new LambdaQueryWrapper<EcoAchievement>().eq(EcoAchievement::getCode, code));
        if (def == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "成就不存在");
        }
        long completedRecycle = recycleOrderMapper.selectCount(new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getUserId, userId)
                .eq(RecycleOrder::getStatus, OrderStatusEnum.COMPLETED.getCode()));
        boolean kitchenFirst = kitchenReportMapper.selectCount(
                new LambdaQueryWrapper<KitchenReport>().eq(KitchenReport::getUserId, userId)) > 0;
        int maxStreak = maxStreak(signDates(userId));
        boolean alreadyUnlocked = userAchievementMapper.selectCount(
                new LambdaQueryWrapper<EcoUserAchievement>()
                        .eq(EcoUserAchievement::getUserId, userId)
                        .eq(EcoUserAchievement::getAchievementId, def.getId())) > 0;
        if (!alreadyUnlocked && !unlocked(def.getCode(), completedRecycle, kitchenFirst, maxStreak)) {
            throw new BusinessException("成就尚未达成，暂不能领取");
        }
        EcoUserAchievement mine = userAchievementMapper.selectOne(new LambdaQueryWrapper<EcoUserAchievement>()
                .eq(EcoUserAchievement::getUserId, userId)
                .eq(EcoUserAchievement::getAchievementId, def.getId()));
        if (mine == null) {
            mine = new EcoUserAchievement();
            mine.setUserId(userId);
            mine.setAchievementId(def.getId());
            mine.setClaimed(1);
            try {
                userAchievementMapper.insert(mine);
            } catch (DuplicateKeyException e) {
                // 并发领取：uk 兜底，按已领取幂等返回
                log.info("[Eco] duplicate claim userId={} code={}", userId, code);
                return EcoClaimVO.builder().code(code).points(def.getPoints()).build();
            }
        } else if (mine.getClaimed() == 1) {
            // 已领取：幂等返回，不重复入账（账本 ECO:{achievementId}:GAIN 双保险）
            return EcoClaimVO.builder().code(code).points(def.getPoints()).build();
        } else {
            mine.setClaimed(1);
            userAchievementMapper.updateById(mine);
        }
        carbonLedger.grantEco(userId, def.getId(), def.getPoints(), "ECO_ACHV_" + code,
                "环保成就「" + def.getName() + "」+" + def.getPoints() + " 分");
        log.info("[Eco] claim userId={} code={} points={}", userId, code, def.getPoints());
        return EcoClaimVO.builder().code(code).points(def.getPoints()).build();
    }

    // ---------- 内部实现 ----------

    /** 自动解锁：eco_user_achievement 不存在则写行（claimed=0 待领取），已存在不动（保留领取态） */
    private void unlock(Long userId, String code) {
        EcoAchievement def = achievementMapper.selectOne(
                new LambdaQueryWrapper<EcoAchievement>().eq(EcoAchievement::getCode, code));
        if (def == null) {
            return;
        }
        if (userAchievementMapper.selectCount(new LambdaQueryWrapper<EcoUserAchievement>()
                .eq(EcoUserAchievement::getUserId, userId)
                .eq(EcoUserAchievement::getAchievementId, def.getId())) > 0) {
            return;
        }
        EcoUserAchievement row = new EcoUserAchievement();
        row.setUserId(userId);
        row.setAchievementId(def.getId());
        row.setClaimed(0);
        try {
            userAchievementMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 并发解锁：uk 兜底，已存在即视为已解锁
        }
    }

    /** 达成判定（业务数据侧，SIGN_SEVEN 由签到解锁 + 此处兜底） */
    private boolean unlocked(String code, long completedRecycle, boolean kitchenFirst, int maxStreak) {
        return switch (code) {
            case CODE_RECYCLE_FIRST -> completedRecycle >= 1;
            case CODE_RECYCLE_TEN -> completedRecycle >= 10;
            case CODE_KITCHEN_FIRST -> kitchenFirst;
            case CODE_SIGN_SEVEN -> maxStreak >= streakDaysRequired();
            default -> false;
        };
    }

    /** 用户签到日期（升序去重） */
    private List<LocalDate> signDates(Long userId) {
        return signInMapper.selectList(new LambdaQueryWrapper<EcoSignIn>()
                        .eq(EcoSignIn::getUserId, userId)
                        .orderByAsc(EcoSignIn::getSignDate))
                .stream().map(EcoSignIn::getSignDate).distinct().toList();
    }

    /** 连续签到天数（截至今天；今天未签则从昨天回溯） */
    private int streakDays(Long userId) {
        List<LocalDate> dates = signDates(userId);
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate expect = LocalDate.now();
        if (!dates.contains(expect)) {
            expect = expect.minusDays(1);
        }
        int streak = 0;
        for (int i = dates.size() - 1; i >= 0; i--) {
            if (dates.get(i).equals(expect)) {
                streak++;
                expect = expect.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    /** 历史最大连续天数（成就判定用，允许跨中断累计） */
    private int maxStreak(List<LocalDate> dates) {
        int max = 0;
        int cur = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            cur = prev != null && d.equals(prev.plusDays(1)) ? cur + 1 : 1;
            max = Math.max(max, cur);
            prev = d;
        }
        return max;
    }

    private int signPoints() {
        return Integer.parseInt(configService.getValue(CFG_SIGN_POINTS, DEFAULT_SIGN_POINTS));
    }

    private int streakDaysRequired() {
        return Integer.parseInt(configService.getValue(CFG_STREAK_DAYS, DEFAULT_STREAK_DAYS));
    }
}

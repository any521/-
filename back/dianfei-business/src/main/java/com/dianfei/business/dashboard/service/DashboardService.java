package com.dianfei.business.dashboard.service;

import com.dianfei.business.dashboard.mapper.DashboardMapper;
import com.dianfei.business.dashboard.vo.DashboardOverviewVO;
import com.dianfei.business.dashboard.vo.DashboardSummaryVO;
import com.dianfei.business.dashboard.vo.AreaRankVO;
import com.dianfei.business.dashboard.vo.MonthlyReportVO;
import com.dianfei.business.support.SysUserBriefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * 数据大屏聚合服务（C5，对齐 11.2 大屏聚合性能项）：
 * 只读聚合走 DashboardMapper；MVP 单库先加进程内快照缓存（TTL 1s），
 * 大屏 1s 轮询命中同一快照不重复打库；演进阶段再切只读副本/Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 快照缓存窗口：大屏 1s 刷新节奏内共享同一次聚合结果 */
    private static final long CACHE_TTL_MS = 1000L;
    /** 月度报表回看窗口（含当月） */
    private static final int MONTH_WINDOW = 12;

    private final DashboardMapper dashboardMapper;
    private final SysUserBriefMapper sysUserBriefMapper;

    private volatile CachedSnapshot cache;
    private volatile CachedSummary summaryCache;

    /** 总览：缓存窗口内直接返回快照，过期后重建（双检避免并发重复聚合） */
    public DashboardOverviewVO overview() {
        CachedSnapshot snapshot = cache;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - snapshot.bornAt < CACHE_TTL_MS) {
            return snapshot.vo;
        }
        synchronized (this) {
            snapshot = cache;
            now = System.currentTimeMillis();
            if (snapshot != null && now - snapshot.bornAt < CACHE_TTL_MS) {
                return snapshot.vo;
            }
            DashboardOverviewVO vo = buildOverview();
            cache = new CachedSnapshot(vo, now);
            return vo;
        }
    }

    /** 强制重建快照（后台手动刷新/单测用） */
    public synchronized DashboardOverviewVO refresh() {
        DashboardOverviewVO vo = buildOverview();
        cache = new CachedSnapshot(vo, System.currentTimeMillis());
        return vo;
    }

    /**
     * 工作台摘要（G6）：今日单量/重量/碳/用户数 + 近 7 日趋势 + 品类占比 + 片区排名，
     * 与 overview 同一 1s 快照缓存节奏（大屏与工作台同窗口共享聚合结果）。
     */
    public DashboardSummaryVO summary() {
        CachedSummary snapshot = summaryCache;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - snapshot.bornAt < CACHE_TTL_MS) {
            return snapshot.vo;
        }
        synchronized (this) {
            snapshot = summaryCache;
            now = System.currentTimeMillis();
            if (snapshot != null && now - snapshot.bornAt < CACHE_TTL_MS) {
                return snapshot.vo;
            }
            DashboardSummaryVO vo = buildSummary();
            summaryCache = new CachedSummary(vo, now);
            return vo;
        }
    }

    private DashboardSummaryVO buildSummary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setTodayOrders((int) dashboardMapper.selectTodayOrders(todayStart, tomorrowStart));

        BigDecimal todayWeight = dashboardMapper.selectTodayFinishedWeight(todayStart, tomorrowStart);
        vo.setTodayWeight(todayWeight);

        BigDecimal todayCarbon = dashboardMapper.selectTodayCarbon(todayStart, tomorrowStart);
        if (todayCarbon.signum() == 0) {
            // 无账本流水：按回收重量 × RECYCLE 规则碳系数均值折算（口径对齐 11.2 测算）
            BigDecimal perKg = dashboardMapper.selectAvgRecycleCarbonPerKg();
            if (perKg != null) {
                todayCarbon = todayWeight.multiply(perKg).setScale(2, RoundingMode.HALF_UP);
            }
        }
        vo.setTodayCarbon(todayCarbon);

        vo.setTotalUsers(sysUserBriefMapper.countAll());
        vo.setWeekTrend(weekTrend());
        vo.setCategoryRatio(categoryRatio());
        vo.setAreaRank(areaRank());
        return vo;
    }

    /** 近 7 日趋势：回收按 create_time、重量按 finish_time、碳按流水 create_time，缺失日补 0 */
    private List<DashboardSummaryVO.DayStat> weekTrend() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<Integer, DashboardSummaryVO.DayStat> byDay = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            DashboardSummaryVO.DayStat stat = new DashboardSummaryVO.DayStat();
            stat.setDate(d.format(DateTimeFormatter.ofPattern("MM-dd")));
            stat.setOrders(0L);
            stat.setWeight(BigDecimal.ZERO);
            stat.setCarbon(BigDecimal.ZERO);
            byDay.put(d.getDayOfMonth(), stat);
        }

        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        for (DashboardMapper.DayRow r : dashboardMapper.selectWeekRecycleOrders(from, to)) {
            DashboardSummaryVO.DayStat stat = byDay.get(r.getD());
            if (stat != null) {
                stat.setOrders(r.getCnt());
            }
        }
        for (DashboardMapper.DayRow r : dashboardMapper.selectWeekFinishedWeight(from, to)) {
            DashboardSummaryVO.DayStat stat = byDay.get(r.getD());
            if (stat != null) {
                stat.setWeight(r.getAmount());
            }
        }
        for (DashboardMapper.DayRow r : dashboardMapper.selectWeekCarbon(from, to)) {
            DashboardSummaryVO.DayStat stat = byDay.get(r.getD());
            if (stat != null) {
                stat.setCarbon(r.getAmount());
            }
        }
        return new ArrayList<>(byDay.values());
    }

    /** 回收品类占比 Top5（名称用字典 label，未知品类回退原始码） */
    private List<DashboardSummaryVO.NameValue> categoryRatio() {
        return dashboardMapper.selectCategoryCountTop5().stream()
                .map(c -> {
                    DashboardSummaryVO.NameValue nv = new DashboardSummaryVO.NameValue();
                    nv.setName(CATEGORY_LABELS.getOrDefault(c.getCategory(), c.getCategory()));
                    nv.setValue(c.getCnt() == null ? 0 : c.getCnt().intValue());
                    return nv;
                })
                .toList();
    }

    /** 片区回收重量排名（复用 overview 的 areaRanking） */
    private List<DashboardSummaryVO.AreaRankItem> areaRank() {
        return overview().getAreaRanking().stream()
                .map(a -> {
                    DashboardSummaryVO.AreaRankItem item = new DashboardSummaryVO.AreaRankItem();
                    item.setName(a.getAreaName());
                    item.setWeight(a.getRecycleWeight());
                    return item;
                })
                .toList();
    }

    private DashboardOverviewVO buildOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        DashboardOverviewVO vo = new DashboardOverviewVO();

        DashboardMapper.RecycleStat recycle = dashboardMapper.selectRecycleStat(todayStart);
        vo.setRecycleTotalOrders(recycle.getTotalOrders());
        vo.setRecycleFinishedOrders(recycle.getFinishedOrders());
        vo.setRecycleTotalWeight(recycle.getTotalWeight());
        vo.setRecycleTodayOrders(recycle.getTodayOrders());

        DashboardMapper.KitchenStat kitchen = dashboardMapper.selectKitchenStat(todayStart);
        vo.setKitchenTotalBags(kitchen.getTotalBags());
        vo.setKitchenTodayBags(kitchen.getTodayBags());

        DashboardMapper.RepairStat repair = dashboardMapper.selectRepairStat();
        vo.setRepairTotalOrders(repair.getTotalOrders());
        vo.setRepairFinishedOrders(repair.getFinishedOrders());
        vo.setRepairTransferredOrders(repair.getTransferredOrders());

        vo.setCarbonReducedTotal(dashboardMapper.selectCarbonReducedTotal());
        vo.setAreaRanking(areaRankingWithCarbonFallback());
        vo.setMonthlyReport(monthlyReport());
        vo.setGeneratedAt(LocalDateTime.now());
        return vo;
    }

    /**
     * 片区排行 + 碳减排兑底：账本无 RECYCLE 流水时按回收重量 × 平均碳系数折算
     * （口径与 summary 今日碳一致，保证驾驶舱地图每个片区均有碳减排量可标注）
     */
    private List<AreaRankVO> areaRankingWithCarbonFallback() {
        List<AreaRankVO> ranking = dashboardMapper.selectAreaRanking();
        boolean anyCarbon = ranking.stream()
                .anyMatch(a -> a.getCarbonReduced() != null && a.getCarbonReduced().signum() > 0);
        if (!anyCarbon) {
            BigDecimal perKg = dashboardMapper.selectAvgRecycleCarbonPerKg();
            if (perKg != null) {
                ranking.forEach(a -> a.setCarbonReduced(a.getRecycleWeight() == null ? BigDecimal.ZERO
                        : a.getRecycleWeight().multiply(perKg).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        return ranking;
    }

    /** 近 12 个月月度报表：四域分组查询按 yyyy-MM 合并，缺月补零行保证图表连续 */
    public List<MonthlyReportVO> monthlyReport() {
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(MONTH_WINDOW - 1L);
        LocalDateTime from = start.atDay(1).atStartOfDay();

        Map<String, MonthlyReportVO> byMonth = new TreeMap<>();
        for (YearMonth ym = start; !ym.isAfter(current); ym = ym.plusMonths(1)) {
            MonthlyReportVO row = new MonthlyReportVO();
            row.setMonth(ym.toString());
            byMonth.put(ym.toString(), row);
        }

        merge(byMonth, dashboardMapper.selectMonthlyRecycle(from), (row, r) -> {
            row.setRecycleOrders(r.getCnt());
            row.setRecycleWeight(r.getAmount());
        });
        merge(byMonth, dashboardMapper.selectMonthlyKitchen(from),
                (row, r) -> row.setKitchenBags(r.getAmount() == null ? 0L : r.getAmount().longValue()));
        merge(byMonth, dashboardMapper.selectMonthlyRepair(from), (row, r) -> row.setRepairOrders(r.getCnt()));
        merge(byMonth, dashboardMapper.selectMonthlyCarbon(from), (row, r) -> row.setCarbonReduced(
                r.getAmount() == null ? BigDecimal.ZERO : r.getAmount()));
        return new ArrayList<>(byMonth.values());
    }

    private static void merge(Map<String, MonthlyReportVO> byMonth, List<DashboardMapper.MonthRow> rows,
                              BiConsumer<MonthlyReportVO, DashboardMapper.MonthRow> setter) {
        for (DashboardMapper.MonthRow row : rows) {
            String key = YearMonth.of(row.getY(), row.getM()).toString();
            MonthlyReportVO target = byMonth.get(key);
            if (target != null) {
                setter.accept(target, row);
            }
        }
    }

    /** 回收品类字典 label（与 09.3 字典一致；未知品类回退原始码） */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "METAL", "金属", "PLASTIC", "塑料", "PAPER", "纸类",
            "FABRIC", "织物", "E_WASTE", "电子废弃物");

    private record CachedSnapshot(DashboardOverviewVO vo, long bornAt) {
    }

    private record CachedSummary(DashboardSummaryVO vo, long bornAt) {
    }
}

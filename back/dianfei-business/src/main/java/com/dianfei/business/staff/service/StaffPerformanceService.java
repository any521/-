package com.dianfei.business.staff.service;

import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.mapper.StaffPerformanceMapper;
import com.dianfei.business.staff.mapper.StaffPerformanceMapper.DayRow;
import com.dianfei.business.staff.mapper.StaffPerformanceMapper.KitchenAgg;
import com.dianfei.business.staff.mapper.StaffPerformanceMapper.RecycleAgg;
import com.dianfei.business.staff.mapper.StaffPerformanceMapper.StaffOrdersRow;
import com.dianfei.business.staff.vo.PerformanceVO;
import com.dianfei.business.staff.vo.PerformanceVO.DayStat;
import com.dianfei.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 员工绩效（G10）：按月聚合完成单数/重量/金额/碳减排 + 片区排名 + 近 7 日曲线；
 * praiseRate 按当月该员工订单评价聚合（rating>=4 占比 ×100，无评价返回 0，前端展示「—」）。
 */
@Service
@RequiredArgsConstructor
public class StaffPerformanceService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StaffService staffService;
    private final StaffPerformanceMapper performanceMapper;

    public PerformanceVO monthPerformance(Long userId, String month) {
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        YearMonth ym = parseMonth(month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        RecycleAgg recycle = performanceMapper.selectRecycleAgg(staff.getId(), from, to);
        KitchenAgg kitchen = performanceMapper.selectKitchenAgg(staff.getId(), from, to);
        long repair = performanceMapper.selectRepairCount(staff.getId(), from, to);
        long orders = recycle.getOrders() + kitchen.getOrders() + repair;

        PerformanceVO vo = new PerformanceVO();
        vo.setMonth(ym.format(MONTH_FMT));
        vo.setOrders(orders);
        vo.setWeight(recycle.getWeight().add(kitchen.getWeight()));
        vo.setAmount(recycle.getAmount());
        vo.setCarbon(performanceMapper.selectRecycleCarbon(staff.getId(), from, to));
        vo.setRank(calcRank(staff, orders, from, to));
        vo.setPraiseRate(praiseRate(staff.getId(), from, to));
        vo.setDays(buildDays(staff.getId(), to));
        return vo;
    }

    /** 当月好评率 %：该员工订单评价 rating>=4 占比 ×100，保留 1 位小数；无评价返回 0 */
    private BigDecimal praiseRate(Long staffId, LocalDateTime from, LocalDateTime to) {
        BigDecimal rate = performanceMapper.selectPraiseRate(staffId, from, to);
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        return rate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    /** 同片区在岗他人按当月单数降序排名：他人单数 > 本人 的数量 + 1；无他人 = 1 */
    private int calcRank(BizStaff staff, long myOrders, LocalDateTime from, LocalDateTime to) {
        List<StaffOrdersRow> peers = performanceMapper
                .selectAreaStaffOrders(staff.getAreaId(), staff.getId(), from, to);
        if (peers.isEmpty()) {
            return 1;
        }
        long better = peers.stream().filter(p -> p.getOrders() != null && p.getOrders() > myOrders).count();
        return (int) better + 1;
    }

    /** 近 7 日（含今日）按日完成单数，缺失日补 0；以 LocalDate.now() 为右端，与月份参数无关 */
    private List<DayStat> buildDays(Long staffId, LocalDateTime to) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        List<DayRow> rows = performanceMapper.selectDayOrders(staffId, start.atStartOfDay());
        Map<LocalDate, Long> countByDay = rows.stream()
                .collect(Collectors.toMap(DayRow::getD, DayRow::getC, Long::sum));
        List<DayStat> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            days.add(new DayStat(day.format(DAY_FMT), countByDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month, MONTH_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式不正确，应为 yyyy-MM");
        }
    }
}

package com.dianfei.business.carbon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.vo.CarbonAccountVO;
import com.dianfei.business.carbon.vo.CarbonBillVO;
import com.dianfei.common.enums.CarbonLogTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 碳账户查询（B2）：账户余额 / 流水分页 / 低碳账单月度聚合
 */
@Service
@RequiredArgsConstructor
public class CarbonQueryService {

    private final CarbonAccountMapper accountMapper;
    private final CarbonLogMapper logMapper;

    public CarbonAccountVO account(Long userId) {
        CarbonAccount account = findAccount(userId);
        if (account == null) {
            return CarbonAccountVO.builder()
                    .pointsBalance(0).frozenPoints(0)
                    .totalCarbonReduced(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .monthCarbon(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .rankPercent(100)
                    .build();
        }
        BigDecimal monthCarbon = logMapper.sumMonthCarbon(account.getId(),
                LocalDate.now().withDayOfMonth(1).atStartOfDay());
        BigDecimal carbon = account.getTotalCarbonReduced();
        long higher = accountMapper.countHigherThan(carbon == null ? BigDecimal.ZERO : carbon);
        long total = accountMapper.countAll();
        int rankPercent = total <= 1 ? 100 : (int) ((total - higher) * 100.0 / total);
        return CarbonAccountVO.builder()
                .pointsBalance(account.getPointsBalance())
                .frozenPoints(account.getFrozenPoints())
                .totalCarbonReduced(account.getTotalCarbonReduced())
                .monthCarbon(monthCarbon == null ? BigDecimal.ZERO
                        : monthCarbon.setScale(4, RoundingMode.HALF_UP))
                .rankPercent(rankPercent)
                .build();
    }

    public PageVO<CarbonLog> logs(Long userId, int pageNum, int pageSize) {
        CarbonAccount account = findAccount(userId);
        if (account == null) {
            return PageVO.of(0, 0, List.of());
        }
        return PageVO.of(logMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CarbonLog>()
                        .eq(CarbonLog::getAccountId, account.getId())
                        .orderByDesc(CarbonLog::getId)));
    }

    /** 低碳账单：按月聚合获得/支出与减排量、按日减排曲线、按业务类型分项；
     *  前端契约字段 totalCarbon/totalPoints/days/treeEquivalent（G13）；month 缺省/空白查当月 */
    public CarbonBillVO bill(Long userId, String month) {
        YearMonth ym;
        if (month == null || month.isBlank()) {
            ym = YearMonth.now();
        } else {
            try {
                ym = YearMonth.parse(month);
            } catch (DateTimeParseException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "month 格式应为 yyyy-MM");
            }
        }
        CarbonAccount account = findAccount(userId);
        List<CarbonLog> logs = account == null ? List.of()
                : logMapper.selectList(new LambdaQueryWrapper<CarbonLog>()
                        .eq(CarbonLog::getAccountId, account.getId())
                        .ge(CarbonLog::getCreateTime, ym.atDay(1).atStartOfDay())
                        .lt(CarbonLog::getCreateTime, ym.plusMonths(1).atDay(1).atStartOfDay())
                        .orderByAsc(CarbonLog::getId));
        long gained = 0;
        long spent = 0;
        long totalPoints = 0;
        BigDecimal carbon = BigDecimal.ZERO;
        Map<LocalDate, BigDecimal> carbonByDay = new HashMap<>();
        Map<String, List<CarbonLog>> byType = new LinkedHashMap<>();
        for (CarbonLog row : logs) {
            if (row.getPoints() > 0) {
                gained += row.getPoints();
            } else {
                spent += -row.getPoints();
            }
            if (CarbonLogTypeEnum.GAIN.getCode() == row.getType()) {
                totalPoints += row.getPoints();
            }
            BigDecimal rowCarbon = row.getCarbonReduced() == null ? BigDecimal.ZERO : row.getCarbonReduced();
            carbon = carbon.add(rowCarbon);
            if (CarbonLogTypeEnum.GAIN.getCode() == row.getType() && row.getCreateTime() != null) {
                carbonByDay.merge(row.getCreateTime().toLocalDate(), rowCarbon, BigDecimal::add);
            }
            byType.computeIfAbsent(row.getOrderType() == null ? "OTHER" : row.getOrderType(),
                    k -> new ArrayList<>()).add(row);
        }
        // 按日曲线：缺失日补 0，按日升序（G13）
        List<CarbonBillVO.DayCarbon> days = new ArrayList<>(ym.lengthOfMonth());
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            days.add(CarbonBillVO.DayCarbon.builder()
                    .date(date.toString())
                    .carbon(carbonByDay.getOrDefault(date, BigDecimal.ZERO))
                    .build());
        }
        List<CarbonBillVO.Item> items = byType.entrySet().stream()
                .map(e -> CarbonBillVO.Item.builder()
                        .orderType(e.getKey())
                        .count(e.getValue().size())
                        .points(e.getValue().stream().mapToLong(CarbonLog::getPoints).sum())
                        .carbonReduced(e.getValue().stream()
                                .map(r -> r.getCarbonReduced() == null ? BigDecimal.ZERO : r.getCarbonReduced())
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .toList();
        BigDecimal totalCarbon = carbon.setScale(4, RoundingMode.HALF_UP);
        return CarbonBillVO.builder()
                .month(ym.toString())
                .totalGained(gained)
                .totalSpent(spent)
                .totalCarbonReduced(totalCarbon)
                .totalCarbon(totalCarbon)
                .totalPoints(totalPoints)
                .days(days)
                .treeEquivalent(totalCarbon.divide(BigDecimal.valueOf(18), 1, RoundingMode.HALF_UP))
                .items(items)
                .build();
    }

    private CarbonAccount findAccount(Long userId) {
        return accountMapper.selectOne(
                new LambdaQueryWrapper<CarbonAccount>().eq(CarbonAccount::getUserId, userId));
    }
}

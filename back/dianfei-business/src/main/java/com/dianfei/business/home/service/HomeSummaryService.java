package com.dianfei.business.home.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.home.mapper.LatestNoticeBrief;
import com.dianfei.business.home.mapper.SysNoticeBriefMapper;
import com.dianfei.business.home.vo.HomeSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 用户端首页摘要（G7）：积分/累计减排/本月减排/最新公告。
 * 不复用 CarbonQueryService 的 VO 壳，直查账户行与流水；公告只读直查 sys_notice（仅公告 notice_type=1）。
 */
@Service
@RequiredArgsConstructor
public class HomeSummaryService {

    private final CarbonAccountMapper accountMapper;
    private final CarbonLogMapper logMapper;
    private final SysNoticeBriefMapper noticeMapper;

    public HomeSummaryVO summary(Long userId) {
        CarbonAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<CarbonAccount>().eq(CarbonAccount::getUserId, userId));
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        BigDecimal monthCarbon = account == null ? BigDecimal.ZERO
                : logMapper.sumMonthCarbon(account.getId(), monthStart.atStartOfDay());
        LatestNoticeBrief brief = noticeMapper.selectLatestNotice();
        return HomeSummaryVO.builder()
                .points(account == null ? 0L : account.getPointsBalance())
                .totalCarbon(account == null ? BigDecimal.ZERO : account.getTotalCarbonReduced())
                .monthCarbon(monthCarbon == null ? BigDecimal.ZERO
                        : monthCarbon.setScale(4, RoundingMode.HALF_UP))
                .notice(brief == null ? "" : brief.getTitle())
                .noticePublishTime(brief == null ? null : brief.getPublishTime())
                .build();
    }
}

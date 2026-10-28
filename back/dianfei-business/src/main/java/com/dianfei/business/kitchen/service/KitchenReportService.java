package com.dianfei.business.kitchen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.entity.CarbonRule;
import com.dianfei.business.carbon.mapper.CarbonRuleMapper;
import com.dianfei.business.kitchen.dto.KitchenReportAuditDTO;
import com.dianfei.business.kitchen.dto.KitchenReportCreateDTO;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.mapper.KitchenReportMapper;
import com.dianfei.business.kitchen.vo.AreaBagAggVO;
import com.dianfei.business.kitchen.vo.KitchenAreaStatsVO;
import com.dianfei.business.kitchen.vo.KitchenMyStatsVO;
import com.dianfei.business.kitchen.vo.KitchenRecordVO;
import com.dianfei.business.map.entity.BizStation;
import com.dianfei.business.map.mapper.BizStationMapper;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.business.support.BizConfigReader;
import com.dianfei.business.support.SysUserBriefMapper;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 垃圾投放上报（B4）：投放时段内上报袋数+投放点+照片，
 * 距垃圾回收站（KITCHEN）50m 内才允许；一律转工作人员审核（0 待审核），
 * 审核通过即发分（账本 bizKey 幂等），归档时对已通过单幂等兜底（见 KitchenTaskService）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenReportService extends ServiceImpl<KitchenReportMapper, KitchenReport> {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 投放半径硬限制：距最近启用 KITCHEN 站点直线距离须 ≤50 米 */
    private static final int STATION_RADIUS_METERS = 50;

    /** 垃圾投放按袋计分，规则表 category 约定 DEFAULT（品类字典驱动，对齐 09.3） */
    private static final String KITCHEN_CATEGORY = "DEFAULT";

    private final AreaService areaService;
    private final StaffService staffService;
    private final BizStationMapper stationMapper;
    private final CarbonLedgerOps carbonLedger;
    private final BizConfigReader configReader;
    private final MessageSender messageSender;
    private final SysUserBriefMapper sysUserBriefMapper;
    private final CarbonRuleMapper carbonRuleMapper;
    private final Clock clock;

    /**
     * 用户上报（POST /api/kitchen/reports）：
     * 时段校验（默认 20-23 点，sys_config 热更新）→ 距垃圾回收站 50m 校验 →
     * 片区兜底解析 → 落库（一律 0 待审核，工作人员审核通过后才发分）
     */
    @Transactional
    public KitchenReport report(Long userId, KitchenReportCreateDTO dto) {
        LocalDateTime now = LocalDateTime.now(clock);
        requireReportWindow(now);
        requireWithinStation(dto.getLng(), dto.getLat());
        Long areaId = resolveAreaId(dto.getAreaId(), dto.getLng(), dto.getLat());

        KitchenReport report = new KitchenReport();
        report.setUserId(userId);
        report.setAreaId(areaId);
        report.setPointName(dto.getPointName());
        report.setBagCount(dto.getBagCount());
        report.setPhotoUrl(dto.getPhotoUrl());
        report.setLng(dto.getLng());
        report.setLat(dto.getLat());
        report.setReportTime(now);
        report.setStatus(KitchenReport.STATUS_PENDING);
        save(report);

        messageSender.send(userId, MessageSender.TYPE_KITCHEN_PENDING,
                "投放上报已提交", "投放 " + dto.getBagCount()
                        + " 袋已登记，待工作人员审核后发放积分");
        return report;
    }

    /**
     * 片区解析：areaId 存在则校验启用（失效且带坐标 → 按坐标自动重建）；为空但有经纬度 →
     * 自动创建/匹配（精确到村）；两者皆无 → 明确报错（同 B3 下单约定）
     */
    private Long resolveAreaId(Long areaId, BigDecimal lng, BigDecimal lat) {
        if (areaId != null) {
            try {
                areaService.requireEnabled(areaId);
                return areaId;
            } catch (BusinessException e) {
                // 历史失效片区（旧种子被清理 / 雪花 ID 截断）：带坐标则按坐标自动创建/匹配，兼容旧地址簿数据
                if (lng != null && lat != null) {
                    return areaService.createOrMatchAreaByLocation(lng.doubleValue(), lat.doubleValue());
                }
                throw e;
            }
        }
        if (lng != null && lat != null) {
            return areaService.createOrMatchAreaByLocation(lng.doubleValue(), lat.doubleValue());
        }
        throw new BusinessException("片区不存在或已停用，请先定位或选择片区");
    }

    /** 距最近启用垃圾回收站（KITCHEN）直线距离 > 50m 拒绝投放 */
    private void requireWithinStation(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            throw new BusinessException("投放定位不能为空，请开启定位后重试");
        }
        List<BizStation> stations = stationMapper.selectList(new LambdaQueryWrapper<BizStation>()
                .eq(BizStation::getStatus, 1)
                .eq(BizStation::getStationType, "KITCHEN"));
        if (stations.isEmpty()) {
            throw new BusinessException("暂无可投放的垃圾回收站，请联系管理员配置站点");
        }
        double bestDist = Double.MAX_VALUE;
        for (BizStation s : stations) {
            if (s.getLng() == null || s.getLat() == null) {
                continue;
            }
            bestDist = Math.min(bestDist, haversineMeters(lat.doubleValue(), lng.doubleValue(),
                    s.getLat().doubleValue(), s.getLng().doubleValue()));
        }
        if (bestDist > STATION_RADIUS_METERS) {
            throw new BusinessException("距最近垃圾回收站 " + Math.round(bestDist)
                    + " 米，需在 " + STATION_RADIUS_METERS + " 米内才能投放");
        }
    }

    /** Haversine 球面距离（米），与 StationService 同算法 */
    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    // ==================== 员工端审核（工作人员） ====================

    /** 员工端待审核列表：本片区 0 待审核单，按上报时间正序（按 biz_staff.area_id 过滤，不依赖 token 旧片区） */
    public List<KitchenRecordVO> staffPendingReports(Long staffUserId) {
        BizStaff staff = staffService.requireOnDutyByUserId(staffUserId);
        List<KitchenReport> records = lambdaQuery()
                .eq(KitchenReport::getAreaId, staff.getAreaId())
                .eq(KitchenReport::getStatus, KitchenReport.STATUS_PENDING)
                .orderByAsc(KitchenReport::getReportTime)
                .list();
        Map<Long, String> areaNames = areaNameMap(records);
        Map<Long, String> nicknames = nicknameMap(records);
        BigDecimal pointsPerBag = kitchenPointsPerBag();
        return records.stream()
                .map(r -> toVO(r, areaNames, nicknames, pointsPerBag))
                .toList();
    }

    /**
     * 员工端审核（POST /api/staff/kitchen/reports/{id}/audit）：仅本片区 0 待审核单。
     * 通过 → 1 已通过 + 账本发分（bizKey 幂等，归档重复结算不重发）+ 到账通知；
     * 驳回 → 3 已驳回 + 驳回原因 + 通知，不发分。
     */
    @Transactional
    public void audit(Long reportId, Long staffUserId, KitchenReportAuditDTO dto) {
        BizStaff staff = staffService.requireOnDutyByUserId(staffUserId);
        KitchenReport report = getById(reportId);
        if (report == null) {
            throw new BusinessException("投放记录不存在");
        }
        if (!Objects.equals(report.getAreaId(), staff.getAreaId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅可审核本片区投放记录");
        }
        if (report.getStatus() != KitchenReport.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "该记录已审核，请勿重复操作");
        }
        report.setAuditTime(LocalDateTime.now(clock));
        if (Boolean.TRUE.equals(dto.getPass())) {
            report.setStatus(KitchenReport.STATUS_APPROVED);
            updateById(report);
            CarbonLedgerResult result = carbonLedger.grant(CarbonGrantCmd.builder()
                    .userId(report.getUserId())
                    .orderType(OrderTypeEnum.KITCHEN)
                    .orderId(report.getId())
                    .category(KITCHEN_CATEGORY)
                    .quantity(report.getBagCount())
                    .build());
            if (!result.isDuplicated()) {
                messageSender.send(report.getUserId(), MessageSender.TYPE_POINTS_ARRIVED,
                        "垃圾投放积分到账", "投放 " + report.getBagCount() + " 袋，+"
                                + result.getPoints() + " 分，当前余额 " + result.getBalanceAfter());
            }
        } else {
            report.setStatus(KitchenReport.STATUS_REJECTED);
            report.setAuditRemark(dto.getRemark());
            updateById(report);
            String reason = dto.getRemark() == null || dto.getRemark().isBlank()
                    ? "" : "，原因：" + dto.getRemark();
            messageSender.send(report.getUserId(), MessageSender.TYPE_AUDIT_REJECTED,
                    "投放未通过审核", "投放 " + report.getBagCount() + " 袋未通过工作人员审核"
                            + reason + "，本次不计积分");
        }
    }

    /** 用户上报记录分页 */
    public PageVO<KitchenReport> pageMine(Long userId, int pageNum, int pageSize) {
        return PageVO.of(page(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<KitchenReport>()
                .eq(KitchenReport::getUserId, userId)
                .orderByDesc(KitchenReport::getReportTime)));
    }

    /** 个人累计（总上报/总袋数/今日袋数/冻结笔数） */
    public KitchenMyStatsVO myStats(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<KitchenReport> all = lambdaQuery().eq(KitchenReport::getUserId, userId).list();
        long totalBags = all.stream().mapToLong(KitchenReport::getBagCount).sum();
        long todayBags = all.stream()
                .filter(r -> !r.getReportTime().isBefore(today.atStartOfDay())
                        && r.getReportTime().isBefore(today.plusDays(1).atStartOfDay()))
                .mapToLong(KitchenReport::getBagCount).sum();
        long frozen = all.stream().filter(r -> r.getStatus() == KitchenReport.STATUS_PENDING).count();
        return KitchenMyStatsVO.builder()
                .totalReports((long) all.size())
                .totalBags(totalBags)
                .todayBags(todayBags)
                // 待审核笔数（审核通过才发分；字段名兼容旧契约）
                .frozenReports(frozen)
                .build();
    }

    /** 片区看板（GET /api/kitchen/stats/area）：当日袋数对比清运阈值 */
    public KitchenAreaStatsVO areaStats(Long areaId) {
        BizArea area = areaService.requireEnabled(areaId);
        LocalDate today = LocalDate.now(clock);
        long todayBags = 0;
        long todayReports = 0;
        for (AreaBagAggVO agg : baseMapper.aggregateByArea(today.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            if (area.getId().equals(agg.getAreaId())) {
                todayBags = agg.getTotalBags();
                todayReports = agg.getReportCount();
            }
        }
        return KitchenAreaStatsVO.builder()
                .areaId(String.valueOf(area.getId()))
                .areaName(area.getAreaName())
                .todayBags(todayBags)
                .todayReports(todayReports)
                .cleanThreshold(area.getCleanThreshold())
                .thresholdReached(area.getCleanThreshold() != null && todayBags >= area.getCleanThreshold())
                .build();
    }

    /**
     * 管理端投放记录分页（G5，/api/admin/kitchen/records）：片区/状态/关键词过滤，
     * 联 biz_area 片区名 + sys_user 昵称（分页行批量回填），按上报时间倒序。
     * points = 袋数 × KITCHEN/DEFAULT 每袋积分（规则缺失为 0）；aiAuditResult 由 status 映射文案。
     */
    public PageVO<KitchenRecordVO> pageAdmin(PageDTO page, Long areaId, Integer status, String keyword) {
        LambdaQueryWrapper<KitchenReport> wrapper = new LambdaQueryWrapper<KitchenReport>()
                .eq(areaId != null, KitchenReport::getAreaId, areaId)
                .eq(status != null, KitchenReport::getStatus, status)
                .orderByDesc(KitchenReport::getReportTime);
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isEmpty()) {
            // 关键词：用户昵称/用户名 或 投放点 模糊命中
            List<Long> matchedUserIds = sysUserBriefMapper.selectIdsByKeyword(kw);
            wrapper.and(w -> {
                if (!matchedUserIds.isEmpty()) {
                    w.in(KitchenReport::getUserId, matchedUserIds);
                    w.or().like(KitchenReport::getPointName, kw);
                } else {
                    w.like(KitchenReport::getPointName, kw);
                }
            });
        }

        Page<KitchenReport> result = page(new Page<>(page.getPageNum(), page.getPageSize()), wrapper);
        List<KitchenReport> records = result.getRecords();
        Map<Long, String> areaNames = areaNameMap(records);
        Map<Long, String> nicknames = nicknameMap(records);
        BigDecimal pointsPerBag = kitchenPointsPerBag();
        List<KitchenRecordVO> list = records.stream()
                .map(r -> toVO(r, areaNames, nicknames, pointsPerBag))
                .toList();
        return new PageVO<>(result.getTotal(), result.getPages(), list);
    }

    /** 分页行片区名批量回填（deleted=0 由 @TableLogic 自动过滤） */
    private Map<Long, String> areaNameMap(List<KitchenReport> records) {
        List<Long> ids = records.stream().map(KitchenReport::getAreaId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return areaService.listByIds(ids).stream()
                .collect(Collectors.toMap(BizArea::getId, BizArea::getAreaName, (a, b) -> a));
    }

    /** 分页行用户昵称批量回填（sys_user 同库只读） */
    private Map<Long, String> nicknameMap(List<KitchenReport> records) {
        List<Long> ids = records.stream().map(KitchenReport::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserBriefMapper.selectNicknameByIds(ids).stream()
                .collect(Collectors.toMap(SysUserBriefMapper.UserBrief::getId,
                        SysUserBriefMapper.UserBrief::getNickname, (a, b) -> a));
    }

    /** 垃圾投放每袋积分：KITCHEN/DEFAULT 启用规则，缺失或未配置为 0 */
    private BigDecimal kitchenPointsPerBag() {
        CarbonRule rule = carbonRuleMapper.selectOne(new LambdaQueryWrapper<CarbonRule>()
                .eq(CarbonRule::getOrderType, OrderTypeEnum.KITCHEN.getCode())
                .eq(CarbonRule::getCategory, CarbonRule.CATEGORY_DEFAULT)
                .eq(CarbonRule::getStatus, 1)
                .last("LIMIT 1"));
        return rule == null || rule.getPointsPerBag() == null ? BigDecimal.ZERO : rule.getPointsPerBag();
    }

    private KitchenRecordVO toVO(KitchenReport r, Map<Long, String> areaNames,
                                 Map<Long, String> nicknames, BigDecimal pointsPerBag) {
        KitchenRecordVO vo = new KitchenRecordVO();
        vo.setId(r.getId());
        vo.setAreaName(areaNames.getOrDefault(r.getAreaId(), "片区" + r.getAreaId()));
        vo.setUserName(nicknames.getOrDefault(r.getUserId(), "用户" + r.getUserId()));
        vo.setBags(r.getBagCount());
        vo.setPhotoUrl(r.getPhotoUrl());
        vo.setAiAuditResult(auditText(r.getStatus()));
        vo.setPoints(pointsPerBag.multiply(BigDecimal.valueOf(r.getBagCount()))
                .setScale(0, RoundingMode.HALF_UP).intValue());
        vo.setCreateTime(r.getReportTime().format(DATETIME));
        return vo;
    }

    /** 审核文案映射：0 待审核 / 1 已通过 / 2 已复核 / 3 已驳回 */
    private static String auditText(Integer status) {
        if (status == null) {
            return "待审核";
        }
        return switch (status) {
            case KitchenReport.STATUS_APPROVED -> "已通过";
            case KitchenReport.STATUS_REVIEWED -> "已复核";
            case KitchenReport.STATUS_REJECTED -> "已驳回";
            default -> "待审核";
        };
    }

    /** 指定日期指定片区的上报单（归档发分用） */
    public List<KitchenReport> listAreaReports(Long areaId, LocalDate date) {
        return lambdaQuery()
                .eq(KitchenReport::getAreaId, areaId)
                .ge(KitchenReport::getReportTime, date.atStartOfDay())
                .lt(KitchenReport::getReportTime, date.plusDays(1).atStartOfDay())
                .orderByAsc(KitchenReport::getId)
                .list();
    }

    /** 投放时段校验：默认每晚 20-23 点（[start, end) 小时区间，sys_config 热更新） */
    private void requireReportWindow(LocalDateTime now) {
        int start = configReader.getInt(BizConfigReader.KEY_KITCHEN_REPORT_START, 20);
        int end = configReader.getInt(BizConfigReader.KEY_KITCHEN_REPORT_END, 23);
        int hour = now.getHour();
        if (hour < start || hour >= end) {
            throw new BusinessException("垃圾投放仅限每晚 " + start + "-" + end + " 点，请在时段内上报");
        }
    }
}

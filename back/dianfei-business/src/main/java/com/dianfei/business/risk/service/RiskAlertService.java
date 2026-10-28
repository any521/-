package com.dianfei.business.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerService;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.risk.entity.RiskAlert;
import com.dianfei.business.risk.mapper.RiskAlertMapper;
import com.dianfei.business.risk.vo.BrushRiskVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控告警处置（C4）：复用 B4 的 biz_risk_alert 记录，
 * 人工复核 → 账本 reviewFrozen 解冻发放/违规扣除 → 状态回写，同事务；
 * 附恶意刷单检测（高频兑换聚合），处置走 AdminCarbonController 冻结/清零
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAlertService extends ServiceImpl<RiskAlertMapper, RiskAlert> {

    private final CarbonLedgerService carbonLedgerService;
    private final MessageSender messageSender;

    /** 后台：告警分页（状态/片区/业务类型过滤，待复核优先） */
    public PageVO<RiskAlert> pageAlerts(Integer status, Long areaId, String bizType, int pageNum, int pageSize) {
        LambdaQueryWrapper<RiskAlert> wrapper = new LambdaQueryWrapper<RiskAlert>()
                .eq(status != null, RiskAlert::getStatus, status)
                .eq(areaId != null, RiskAlert::getAreaId, areaId)
                .eq(bizType != null && !bizType.isBlank(), RiskAlert::getBizType, bizType)
                .orderByAsc(RiskAlert::getStatus).orderByDesc(RiskAlert::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 人工复核（仅待复核单）：pass=true 解冻返还发放；pass=false 驳回扣除冻结积分。
     * 账本回写与告警状态同事务，失败整体回滚
     */
    @Transactional
    public RiskAlert review(Long alertId, boolean pass) {
        RiskAlert alert = requireAlert(alertId);
        if (alert.getStatus() != RiskAlert.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "该告警已复核");
        }
        carbonLedgerService.reviewFrozen(alert.getUserId(), alert.getFreezeLogId(),
                alert.getFrozenPoints(), pass);
        alert.setStatus(pass ? RiskAlert.STATUS_PASSED : RiskAlert.STATUS_REJECTED);
        updateById(alert);

        messageSender.send(alert.getUserId(), MessageSender.TYPE_POINTS_ARRIVED,
                pass ? "冻结积分已发放" : "冻结积分被扣除",
                pass ? "风控复核通过，" + alert.getFrozenPoints() + " 积分已解冻到账"
                        : "风控复核未通过（" + alert.getReason() + "），" + alert.getFrozenPoints() + " 冻结积分已扣除");
        return alert;
    }

    /** 恶意刷单检测：近 hours 小时内有效兑换单数 ≥ threshold 的用户 */
    public List<BrushRiskVO> brushCheck(int hours, int threshold) {
        if (hours <= 0 || threshold <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "检测窗口与阈值必须为正数");
        }
        return baseMapper.selectBrushSuspects(LocalDateTime.now().minusHours(hours), threshold);
    }

    private RiskAlert requireAlert(Long alertId) {
        RiskAlert alert = getById(alertId);
        if (alert == null) {
            throw new BusinessException("风控告警不存在");
        }
        return alert;
    }
}

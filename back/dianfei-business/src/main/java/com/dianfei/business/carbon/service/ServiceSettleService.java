package com.dianfei.business.carbon.service;

import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.mapper.RepairOrderMapper;
import com.dianfei.business.risk.entity.RiskAlert;
import com.dianfei.business.risk.mapper.RiskAlertMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * 上门报酬积分结算编排（onsite02，契约 C-O2）：
 * 订单完成时由 recycle/repair 完成流程调用，用户承诺报酬积分（自定义 >=1）在服务完成后原子转账给工作人员。
 * 结算失败不阻断订单完成主链路（对齐 D15）：降级 settle_status=2 + 写 biz_risk_alert 转人工。
 * 另承担下单预检（自定义报酬 >=1 + 余额）复用入口，保证校验口径单一。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceSettleService {

    public static final String BIZ_RECYCLE = "RECYCLE";
    public static final String BIZ_REPAIR = "REPAIR";
    /** biz_risk_alert.biz_type：积分结算异常（复用既有告警表，新增业务类型值） */
    public static final String RISK_BIZ_TYPE = "SERVICE_SETTLE";

    /** settle_status：0未结算 1已结算 2免结算/异常转人工 */
    public static final int SETTLE_NONE = 0;
    public static final int SETTLE_DONE = 1;
    public static final int SETTLE_EXCEPTION = 2;

    private final CarbonLedgerOps carbonLedger;
    private final RecycleOrderMapper recycleOrderMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final RiskAlertMapper riskAlertMapper;
    private final MessageSender messageSender;

    // ==================== 下单预检（自定义报酬：0/空=不使用，>=1 合法，余额充足） ====================

    /** servicePoints 为 null 或 0 视为不使用积分支付，直接通过；>=1 为自定义报酬（任意值），余额必须充足 */
    public void validate(Long userId, Long servicePoints, String bizType) {
        if (servicePoints == null || servicePoints == 0) {
            return;
        }
        if (servicePoints < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "报酬积分至少 1 分");
        }
        if (carbonLedger.getBalance(userId) < servicePoints) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH, "碳账户余额不足支付报酬");
        }
    }

    // ==================== 完成结算（2.2：免结算 / 成功 / 失败转人工） ====================

    /**
     * 结算编排：读订单 service_points，<=0 或 settle_status!=0 直接返回（免结算/已结算幂等）；
     * transfer 为 REQUIRES_NEW 独立事务，失败仅回滚流水，本方法捕获后写 settle_status=2 + 风控告警，不抛出。
     */
    @Transactional
    public void settle(Long orderId, String bizType, Long staffUserId) {
        if (BIZ_RECYCLE.equals(bizType)) {
            RecycleOrder order = recycleOrderMapper.selectById(orderId);
            if (order == null) {
                return;
            }
            doSettle(order.getUserId(), order.getServicePoints(), order.getSettleStatus(),
                    order.getOrderNo(), bizType, orderId, staffUserId,
                    st -> {
                        order.setSettleStatus(st);
                        recycleOrderMapper.updateById(order);
                    });
        } else if (BIZ_REPAIR.equals(bizType)) {
            RepairOrder order = repairOrderMapper.selectById(orderId);
            if (order == null) {
                return;
            }
            doSettle(order.getUserId(), order.getServicePoints(), order.getSettleStatus(),
                    order.getOrderNo(), bizType, orderId, staffUserId,
                    st -> {
                        order.setSettleStatus(st);
                        repairOrderMapper.updateById(order);
                    });
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的结算业务类型：" + bizType);
        }
    }

    private void doSettle(Long userId, Long servicePoints, Integer settleStatus, String orderNo,
                          String bizType, Long orderId, Long staffUserId, Consumer<Integer> statusUpdater) {
        if (servicePoints == null || servicePoints <= 0 || settleStatus == null || settleStatus != SETTLE_NONE) {
            return;
        }
        try {
            carbonLedger.transfer(userId, staffUserId, servicePoints, bizType, orderId,
                    "报酬结算：" + bizType + " 单 " + orderNo + " 支付 " + servicePoints + " 积分");
            statusUpdater.accept(SETTLE_DONE);
            messageSender.send(userId, MessageSender.TYPE_POINTS_ARRIVED, "报酬已支付",
                    "服务单 " + orderNo + " 的报酬 " + servicePoints + " 积分已支付给工作人员");
            messageSender.send(staffUserId, MessageSender.TYPE_POINTS_ARRIVED, "报酬已入账",
                    "服务单 " + orderNo + " 的报酬 +" + servicePoints + " 积分已入账，当前可用于平台消费");
            log.info("[Settle] done biz={} order={} points={} from={} to={}", bizType, orderId, servicePoints, userId, staffUserId);
        } catch (BusinessException e) {
            // 结算失败：转人工处置，订单完成主链路不阻断（对齐 D15）
            statusUpdater.accept(SETTLE_EXCEPTION);
            RiskAlert alert = new RiskAlert();
            alert.setBizType(RISK_BIZ_TYPE);
            alert.setBizId(orderId);
            alert.setUserId(userId);
            alert.setReason("报酬结算异常：" + bizType + " 单 " + orderNo + "（" + servicePoints
                    + " 积分），原因：" + e.getMessage());
            alert.setStatus(RiskAlert.STATUS_PENDING);
            riskAlertMapper.insert(alert);
            log.warn("[Settle] failed biz={} order={} points={} from={} to={} reason={}",
                    bizType, orderId, servicePoints, userId, staffUserId, e.getMessage());
        }
    }

    /** 结算结果文案（VO 展示用，字段冻结 settleResult；无报酬返回 null 前端不展示） */
    public static String settleResultText(Long servicePoints, Integer settleStatus) {
        if (servicePoints == null || servicePoints <= 0) {
            return null;
        }
        if (settleStatus == null || settleStatus == SETTLE_NONE) {
            return "报酬将在服务完成后结算";
        }
        if (settleStatus == SETTLE_DONE) {
            return "报酬 " + servicePoints + " 积分 · 已结算给工作人员";
        }
        return "报酬结算异常，平台人工处理中";
    }
}

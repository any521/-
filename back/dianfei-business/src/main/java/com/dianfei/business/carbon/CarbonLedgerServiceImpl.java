package com.dianfei.business.carbon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.dto.CarbonAdjustCmd;
import com.dianfei.business.carbon.dto.CarbonConsumeCmd;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.entity.CarbonRule;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.mapper.CarbonRuleMapper;
import com.dianfei.business.message.MessageSender;
import com.dianfei.common.enums.CarbonLogTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.HashChainUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

/**
 * 可信碳账本真实实现（B2，写账本唯一入口）：
 * 命中 biz_carbon_rule → HashChainUtil 按账户逐条链哈希 → 写流水 → 更新余额与累计减排量。
 * 幂等：biz_key（order_type:order_id:type）唯一键 + 事务内先查；
 * 并发：账户行 FOR UPDATE 锁串行化同账户写入，保证链不分叉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonLedgerServiceImpl implements CarbonLedgerOps {

    private final CarbonAccountMapper accountMapper;
    private final CarbonLogMapper logMapper;
    private final CarbonRuleMapper ruleMapper;
    private final ObjectProvider<MessageSender> messageSender;

    @Override
    @Transactional
    public CarbonLedgerResult grant(CarbonGrantCmd cmd) {
        return doGrant(cmd, false, null);
    }

    @Override
    @Transactional
    public CarbonLedgerResult grantFrozen(CarbonGrantCmd cmd, String reason) {
        return doGrant(cmd, true, reason);
    }

    @Override
    @Transactional
    public CarbonLedgerResult consume(CarbonConsumeCmd cmd) {
        if (cmd.getPoints() <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "扣减积分必须为正数");
        }
        String bizKey = bizKey(cmd.getUserId(), cmd.getOrderType() == null ? null : cmd.getOrderType().getCode(),
                cmd.getOrderId(), CarbonLogTypeEnum.CONSUME);
        CarbonLedgerResult exist = findByBizKey(bizKey);
        if (exist != null) {
            return exist;
        }
        CarbonAccount account = lockOrCreateAccount(cmd.getUserId());
        if (account.getPointsBalance() < cmd.getPoints()) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH);
        }
        long balanceAfter = account.getPointsBalance() - cmd.getPoints();
        CarbonLog logRow = appendLog(account, cmd.getOrderType() == null ? null : cmd.getOrderType().getCode(),
                cmd.getOrderId(), CarbonLogTypeEnum.CONSUME, -cmd.getPoints(), balanceAfter,
                "MALL_CONSUME", BigDecimal.ZERO, bizKey, cmd.getRemark());
        account.setPointsBalance(balanceAfter);
        accountMapper.updateById(account);
        return toResult(logRow, false);
    }

    @Override
    @Transactional
    public CarbonLedgerResult adjust(CarbonAdjustCmd cmd) {
        if (cmd.getPoints() == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "调整积分不能为 0");
        }
        CarbonLogTypeEnum type = cmd.getPoints() > 0 ? CarbonLogTypeEnum.REISSUE : CarbonLogTypeEnum.DEDUCT;
        String bizKey = bizKey(cmd.getUserId(), cmd.getOrderType() == null ? null : cmd.getOrderType().getCode(),
                cmd.getOrderId(), type);
        CarbonLedgerResult exist = findByBizKey(bizKey);
        if (exist != null) {
            return exist;
        }
        CarbonAccount account = lockOrCreateAccount(cmd.getUserId());
        long balanceAfter = account.getPointsBalance() + cmd.getPoints();
        if (balanceAfter < 0) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH, "余额不足以扣减");
        }
        CarbonLog logRow = appendLog(account, cmd.getOrderType() == null ? null : cmd.getOrderType().getCode(),
                cmd.getOrderId(), type, cmd.getPoints(), balanceAfter,
                type == CarbonLogTypeEnum.REISSUE ? "ADMIN_REISSUE" : "ADMIN_DEDUCT",
                BigDecimal.ZERO, bizKey, cmd.getReason());
        account.setPointsBalance(balanceAfter);
        accountMapper.updateById(account);
        return toResult(logRow, false);
    }

    @Override
    @Transactional
    public CarbonLedgerResult grantScan(Long userId, Long orderId, long points, String remark) {
        if (points <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "扫描积分必须为正数");
        }
        String bizKey = bizKey(userId, "SCAN", orderId, CarbonLogTypeEnum.GAIN);
        CarbonLedgerResult exist = findByBizKey(bizKey);
        if (exist != null) {
            return exist;
        }
        CarbonAccount account = lockOrCreateAccount(userId);
        long balanceAfter = account.getPointsBalance() + points;
        CarbonLog logRow = appendLog(account, "SCAN", orderId, CarbonLogTypeEnum.GAIN, points,
                balanceAfter, "SCAN_CLASSIFY", BigDecimal.ZERO, bizKey, remark);
        account.setPointsBalance(balanceAfter);
        account.setTotalCarbonReduced(account.getTotalCarbonReduced().add(normalizeCarbon(BigDecimal.ZERO)));
        accountMapper.updateById(account);
        notify(userId, MessageSender.TYPE_POINTS_ARRIVED, "扫描识别积分到账",
                "本次识别 +" + points + " 分，当前余额 " + balanceAfter);
        return toResult(logRow, false);
    }

    @Override
    @Transactional
    public CarbonLedgerResult grantEco(Long userId, Long bizId, long points, String ruleCode, String remark) {
        if (points <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "生态积分必须为正数");
        }
        String bizKey = bizKey(userId, "ECO", bizId, CarbonLogTypeEnum.GAIN);
        CarbonLedgerResult exist = findByBizKey(bizKey);
        if (exist != null) {
            return exist;
        }
        CarbonAccount account = lockOrCreateAccount(userId);
        long balanceAfter = account.getPointsBalance() + points;
        CarbonLog logRow = appendLog(account, "ECO", bizId, CarbonLogTypeEnum.GAIN, points,
                balanceAfter, ruleCode, BigDecimal.ZERO, bizKey, remark);
        account.setPointsBalance(balanceAfter);
        accountMapper.updateById(account);
        notify(userId, MessageSender.TYPE_POINTS_ARRIVED, "环保积分到账",
                remark + " +" + points + " 分，当前余额 " + balanceAfter);
        return toResult(logRow, false);
    }

    @Override
    @Transactional
    public CarbonLedgerResult clear(Long userId, String reason) {
        CarbonAccount account = lockOrCreateAccount(userId);
        long balance = account.getPointsBalance();
        CarbonLog logRow = appendLog(account, null, null, CarbonLogTypeEnum.CLEAR,
                -balance, 0L, "ADMIN_CLEAR", BigDecimal.ZERO, null, reason);
        account.setPointsBalance(0L);
        accountMapper.updateById(account);
        return toResult(logRow, false);
    }

    @Override
    @Transactional
    public CarbonLedgerResult freeze(Long userId, long points, String reason) {
        return freeze(userId, points, reason, legacyManualKey("FREEZE", userId, points, reason));
    }

    @Override
    @Transactional
    public CarbonLedgerResult freeze(Long userId, long points, String reason, String requestId) {
        if (points <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "冻结积分必须为正数");
        }
        String bizKey = manualBizKey("FREEZE", requestId);
        CarbonLedgerResult existing = findByBizKey(bizKey);
        if (existing != null) {
            return existing;
        }
        CarbonAccount account = lockOrCreateAccount(userId);
        if (account.getPointsBalance() < points) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH, "可用余额不足以冻结");
        }
        long balanceAfter = account.getPointsBalance() - points;
        CarbonLog logRow = appendLog(account, null, null, CarbonLogTypeEnum.FREEZE,
                -points, balanceAfter, "ADMIN_FREEZE", BigDecimal.ZERO, bizKey, reason);
        account.setPointsBalance(balanceAfter);
        account.setFrozenPoints(account.getFrozenPoints() + points);
        accountMapper.updateById(account);
        notify(userId, MessageSender.TYPE_POINTS_FROZEN, "积分冻结提醒",
                "您的 " + points + " 积分已被冻结：" + (reason == null ? "待后台复核" : reason));
        return toResult(logRow, false);
    }

    @Override
    @Transactional
    public CarbonLedgerResult unfreeze(Long userId, long points, String reason) {
        return unfreeze(userId, points, reason, legacyManualKey("UNFREEZE", userId, points, reason));
    }

    @Override
    @Transactional
    public CarbonLedgerResult unfreeze(Long userId, long points, String reason, String requestId) {
        if (points <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "解冻积分必须为正数");
        }
        String bizKey = manualBizKey("UNFREEZE", requestId);
        CarbonLedgerResult existing = findByBizKey(bizKey);
        if (existing != null) {
            return existing;
        }
        CarbonAccount account = lockOrCreateAccount(userId);
        if (account.getFrozenPoints() < points) {
            throw new BusinessException("冻结积分余额不足");
        }
        long balanceAfter = account.getPointsBalance() + points;
        CarbonLog logRow = appendLog(account, null, null, CarbonLogTypeEnum.UNFREEZE,
                points, balanceAfter, "ADMIN_UNFREEZE", BigDecimal.ZERO, bizKey, reason);
        account.setPointsBalance(balanceAfter);
        account.setFrozenPoints(account.getFrozenPoints() - points);
        accountMapper.updateById(account);
        return toResult(logRow, false);
    }

    /**
     * 服务费转账（onsite02，契约 C-O2）：用户→工作人员（biz_staff.user_id）原子转账。
     * REQUIRES_NEW 独立事务：失败仅回滚本笔流水，不把订单完成主链路事务标记为 rollback-only，
     * 由调用方 ServiceSettleService 捕获降级（settle_status=2 + 风控告警）。
     * 两笔流水（转出负/转入正）同批写入，幂等键 TRANSFER:{bizType}:{bizId}:OUT/IN；
     * 账户锁 + 哈希链走账本既有机制，按 userId 升序加锁防交叉转账死锁。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CarbonLedgerResult transfer(Long fromUserId, Long toUserId, long points,
                                       String bizType, Long bizId, String remark) {
        if (points <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "转账积分必须为正数");
        }
        if (fromUserId == null || toUserId == null || fromUserId.longValue() == toUserId.longValue()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "转账账户不合法");
        }
        String outKey = "TRANSFER:" + bizType + ":" + bizId + ":OUT";
        String inKey = "TRANSFER:" + bizType + ":" + bizId + ":IN";
        CarbonLedgerResult exist = findByBizKey(outKey);
        if (exist != null) {
            return exist;
        }
        // 账户锁：按 userId 升序锁定两个账户（防 A→B / B→A 并发互锁）
        boolean fromFirst = fromUserId < toUserId;
        CarbonAccount first = lockOrCreateAccount(fromFirst ? fromUserId : toUserId);
        CarbonAccount second = lockOrCreateAccount(fromFirst ? toUserId : fromUserId);
        CarbonAccount from = fromFirst ? first : second;
        CarbonAccount to = fromFirst ? second : first;
        if (from.getPointsBalance() < points) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH);
        }
        long fromAfter = from.getPointsBalance() - points;
        long toAfter = to.getPointsBalance() + points;
        CarbonLog outLog = appendLog(from, bizType, bizId, CarbonLogTypeEnum.TRANSFER_OUT, -points,
                fromAfter, "TRANSFER_SERVICE", BigDecimal.ZERO, outKey, remark);
        appendLog(to, bizType, bizId, CarbonLogTypeEnum.TRANSFER_IN, points,
                toAfter, "TRANSFER_SERVICE", BigDecimal.ZERO, inKey, remark);
        from.setPointsBalance(fromAfter);
        to.setPointsBalance(toAfter);
        accountMapper.updateById(from);
        accountMapper.updateById(to);
        log.info("[CarbonLedger] transfer from={} to={} points={} biz={}/{} outLogId={}",
                fromUserId, toUserId, points, bizType, bizId, outLog.getId());
        return toResult(outLog, false);
    }

    @Override
    @Transactional
    public void reviewFrozen(Long userId, Long freezeLogId, long frozenPoints, boolean pass) {
        CarbonLog freezeLog = logMapper.selectById(freezeLogId);
        if (freezeLog == null || freezeLog.getType() != CarbonLogTypeEnum.FREEZE.getCode()) {
            throw new BusinessException("冻结流水不存在或类型不符");
        }
        CarbonAccount account = lockOrCreateAccount(userId);
        if (account.getFrozenPoints() < frozenPoints) {
            throw new BusinessException("冻结积分余额不足，疑似重复复核");
        }
        account.setFrozenPoints(account.getFrozenPoints() - frozenPoints);
        if (pass) {
            // 通过：解冻返还余额，累计减排量按冻结时测算值入账
            long balanceAfter = account.getPointsBalance() + frozenPoints;
            appendLog(account, freezeLog.getOrderType(), freezeLog.getOrderId(), CarbonLogTypeEnum.UNFREEZE,
                    frozenPoints, balanceAfter, freezeLog.getRuleCode(), freezeLog.getCarbonReduced(),
                    bizKey(userId, freezeLog.getOrderType(), freezeLog.getOrderId(), CarbonLogTypeEnum.UNFREEZE),
                    "风控复核通过解冻");
            account.setPointsBalance(balanceAfter);
            account.setTotalCarbonReduced(account.getTotalCarbonReduced().add(normalizeCarbon(freezeLog.getCarbonReduced())));
        } else {
            // 驳回：直接扣除冻结积分（未入过可用余额，余额不变）
            appendLog(account, freezeLog.getOrderType(), freezeLog.getOrderId(), CarbonLogTypeEnum.DEDUCT,
                    -frozenPoints, account.getPointsBalance(), freezeLog.getRuleCode(), BigDecimal.ZERO,
                    bizKey(userId, freezeLog.getOrderType(), freezeLog.getOrderId(), CarbonLogTypeEnum.DEDUCT),
                    "风控复核驳回扣除");
        }
        accountMapper.updateById(account);
    }

    @Override
    public long getBalance(Long userId) {
        CarbonAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<CarbonAccount>().eq(CarbonAccount::getUserId, userId));
        return account == null ? 0L : account.getPointsBalance();
    }

    // ---------- 内部实现 ----------

    private CarbonLedgerResult doGrant(CarbonGrantCmd cmd, boolean frozen, String reason) {
        CarbonLogTypeEnum type = frozen ? CarbonLogTypeEnum.FREEZE : CarbonLogTypeEnum.GAIN;
        String orderType = cmd.getOrderType() == null ? null : cmd.getOrderType().getCode();
        String bizKey = bizKey(cmd.getUserId(), orderType, cmd.getOrderId(), type);
        CarbonLedgerResult exist = findByBizKey(bizKey);
        if (exist != null) {
            return exist;
        }
        CarbonRule rule = matchRule(orderType, cmd.getCategory());
        long points = calcPoints(rule, cmd);
        BigDecimal carbon = calcCarbon(rule, cmd);
        CarbonAccount account = lockOrCreateAccount(cmd.getUserId());
        CarbonLog logRow;
        if (frozen) {
            // 冻结发分：入 frozen_points，可用余额不变
            logRow = appendLog(account, orderType, cmd.getOrderId(), type, points,
                    account.getPointsBalance(), rule.getRuleCode(), carbon, bizKey, reason);
            account.setFrozenPoints(account.getFrozenPoints() + points);
            notify(cmd.getUserId(), MessageSender.TYPE_POINTS_FROZEN, "积分冻结提醒",
                    "本次获得的 " + points + " 积分因风控暂被冻结，复核通过后自动到账");
        } else {
            long balanceAfter = account.getPointsBalance() + points;
            logRow = appendLog(account, orderType, cmd.getOrderId(), type, points,
                    balanceAfter, rule.getRuleCode(), carbon, bizKey, null);
            account.setPointsBalance(balanceAfter);
            account.setTotalCarbonReduced(account.getTotalCarbonReduced().add(normalizeCarbon(carbon)));
            notify(cmd.getUserId(), MessageSender.TYPE_POINTS_ARRIVED, "积分到账",
                    "您获得 " + points + " 积分，减排 " + normalizeCarbon(carbon).toPlainString() + " kgCO2");
        }
        accountMapper.updateById(account);
        return toResult(logRow, false);
    }

    /** 规则命中：orderType+category 精确匹配，未中回退 DEFAULT（厨余等无品类业务） */
    private CarbonRule matchRule(String orderType, String category) {
        CarbonRule rule = selectRule(orderType, category);
        if (rule == null && !CarbonRule.CATEGORY_DEFAULT.equals(category)) {
            rule = selectRule(orderType, CarbonRule.CATEGORY_DEFAULT);
        }
        if (rule == null) {
            throw new BusinessException("未配置积分规则：" + orderType + "/" + category);
        }
        return rule;
    }

    private CarbonRule selectRule(String orderType, String category) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<CarbonRule>()
                .eq(CarbonRule::getOrderType, orderType)
                .eq(CarbonRule::getCategory, category == null ? CarbonRule.CATEGORY_DEFAULT : category)
                .eq(CarbonRule::getStatus, 1)
                .last("LIMIT 1"));
    }

    private long calcPoints(CarbonRule rule, CarbonGrantCmd cmd) {
        if (rule.getPointsPerKg() != null && cmd.getWeight() != null) {
            return rule.getPointsPerKg().multiply(cmd.getWeight())
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        if (rule.getPointsPerBag() != null && cmd.getQuantity() != null) {
            return rule.getPointsPerBag().multiply(BigDecimal.valueOf(cmd.getQuantity()))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "发分参数与规则不匹配：需 weight(按公斤) 或 quantity(按袋/件) 与规则单价对应");
    }

    private BigDecimal calcCarbon(CarbonRule rule, CarbonGrantCmd cmd) {
        if (rule.getCarbonPerKg() != null && cmd.getWeight() != null) {
            return normalizeCarbon(rule.getCarbonPerKg().multiply(cmd.getWeight()));
        }
        if (rule.getCarbonPerBag() != null && cmd.getQuantity() != null) {
            return normalizeCarbon(rule.getCarbonPerBag().multiply(BigDecimal.valueOf(cmd.getQuantity())));
        }
        return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    /** 追加一条链上流水：取账户末条哈希续链后 INSERT（唯一键兜底幂等） */
    private CarbonLog appendLog(CarbonAccount account, String orderType, Long orderId,
                                CarbonLogTypeEnum type, long points, long balanceAfter,
                                String ruleCode, BigDecimal carbon, String bizKey, String remark) {
        CarbonLog row = new CarbonLog();
        row.setAccountId(account.getId());
        row.setOrderId(orderId);
        row.setOrderType(orderType);
        row.setType(type.getCode());
        row.setPoints(points);
        row.setBalanceAfter(balanceAfter);
        row.setRuleCode(ruleCode);
        row.setCarbonReduced(normalizeCarbon(carbon));
        row.setBizKey(bizKey);
        row.setRemark(remark);
        CarbonLog last = logMapper.selectOne(new LambdaQueryWrapper<CarbonLog>()
                .eq(CarbonLog::getAccountId, account.getId())
                .orderByDesc(CarbonLog::getId)
                .last("LIMIT 1"));
        row.setPrevHash(last == null ? HashChainUtil.GENESIS_HASH : last.getCurrHash());
        row.setCurrHash(HashChainUtil.chainHash(row.getPrevHash(), hashPayload(row)));
        try {
            logMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发同 bizKey 竞争：事务回滚，调用方重试将命中幂等查询
            throw new BusinessException("账本写入冲突（重复请求），请勿重复提交");
        }
        log.info("[CarbonLedger] append log id={} account={} type={} points={} balanceAfter={} hash={}",
                row.getId(), account.getId(), type, points, balanceAfter, row.getCurrHash());
        return row;
    }

    /** 锁定账户行（无则创建）：串行化同账户余额更新与哈希链续接 */
    private CarbonAccount lockOrCreateAccount(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "userId 不能为空");
        }
        CarbonAccount account = accountMapper.selectByUserIdForUpdate(userId);
        if (account == null) {
            account = new CarbonAccount();
            account.setUserId(userId);
            account.setPointsBalance(0L);
            account.setFrozenPoints(0L);
            account.setTotalCarbonReduced(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
            try {
                accountMapper.insert(account);
            } catch (DuplicateKeyException e) {
                // 并发开户：以已存在行为准
            }
            account = accountMapper.selectByUserIdForUpdate(userId);
        }
        return account;
    }

    private CarbonLedgerResult findByBizKey(String bizKey) {
        if (bizKey == null) {
            return null;
        }
        CarbonLog existing = logMapper.selectOne(
                new LambdaQueryWrapper<CarbonLog>().eq(CarbonLog::getBizKey, bizKey));
        return existing == null ? null : toResult(existing, true);
    }

    private static String manualBizKey(String operation, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "requestId 不能为空");
        }
        return "MANUAL:" + operation + ":" + requestId.trim();
    }

    private static String legacyManualKey(String operation, Long userId, long points, String reason) {
        // 兼容旧内部调用；新管理端必须传入显式 requestId。
        return operation + ":" + userId + ":" + points + ":" + (reason == null ? "" : reason.trim());
    }

    private void notify(Long userId, String type, String title, String content) {
        MessageSender sender = messageSender.getIfAvailable();
        if (sender != null) {
            sender.send(userId, type, title, content);
        }
    }

    private static CarbonLedgerResult toResult(CarbonLog row, boolean duplicated) {
        return CarbonLedgerResult.builder()
                .logId(row.getId())
                .points(row.getPoints())
                .balanceAfter(row.getBalanceAfter())
                .carbonReduced(row.getCarbonReduced())
                .ruleCode(row.getRuleCode())
                .duplicated(duplicated)
                .build();
    }

    private static String bizKey(Long userId, String orderType, Long orderId, CarbonLogTypeEnum type) {
        if (userId == null || orderType == null || orderId == null) {
            return null;
        }
        return userId + ":" + orderType + ":" + orderId + ":" + type.name();
    }

    /** 减排量统一 4 位小数，保证写入与 DB 读回哈希稳定 */
    public static BigDecimal normalizeCarbon(BigDecimal carbon) {
        return (carbon == null ? BigDecimal.ZERO : carbon).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 链哈希 payload（规范化字段集，键按字典序）：
     * 校验方须用同一方法从流水行重建 payload（create_time 由 MP 填充不参与哈希）
     */
    public static Map<String, Object> hashPayload(CarbonLog row) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("accountId", row.getAccountId());
        payload.put("orderId", row.getOrderId());
        payload.put("orderType", row.getOrderType());
        payload.put("type", row.getType());
        payload.put("points", row.getPoints());
        payload.put("balanceAfter", row.getBalanceAfter());
        payload.put("ruleCode", row.getRuleCode());
        payload.put("carbonReduced", normalizeCarbon(row.getCarbonReduced()).toPlainString());
        payload.put("bizKey", row.getBizKey());
        payload.put("remark", row.getRemark());
        return payload;
    }
}

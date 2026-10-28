package com.dianfei.business.carbon;

import com.dianfei.business.carbon.dto.CarbonAdjustCmd;
import com.dianfei.business.carbon.dto.CarbonConsumeCmd;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;

/**
 * 可信碳账本写入唯一入口（Sprint 0 冻结契约，Track B 实现 / Track C、E 复用）。
 *
 * <p>实现要求（对齐 07 章 / trackB B2）：
 * <ul>
 *   <li>幂等：以 bizKey = orderType:orderId:type 保证同单不重复发分/扣分</li>
 *   <li>哈希链：每笔流水 HashChainUtil.chainHash 逐条链接（prev_hash/curr_hash）</li>
 *   <li>事务：写流水 + 更新余额/累计减排量原子完成</li>
 *   <li>余额不足抛 BusinessException(ResultCode.POINTS_NOT_ENOUGH=2006)</li>
 * </ul>
 */
public interface CarbonLedgerService {

    /**
     * 业务完成发分（回收完成 / 厨余归档 / 维修替代换新）：
     * 按 biz_carbon_rule 命中 orderType+category 规则计算积分与减排量
     */
    CarbonLedgerResult grant(CarbonGrantCmd cmd);

    /** 消费扣分（商城兑换等），余额不足抛 2006 */
    CarbonLedgerResult consume(CarbonConsumeCmd cmd);

    /** 后台调整：补发（points 为正）/ 违规扣减（points 为负），售后与风控处置用 */
    CarbonLedgerResult adjust(CarbonAdjustCmd cmd);

    /**
     * 风控冻结复核回写：pass=true 解冻返还；pass=false 驳回并扣除冻结积分
     *
     * @param freezeLogId 冻结时的流水 ID（biz_risk_alert.freeze_log_id）
     */
    void reviewFrozen(Long userId, Long freezeLogId, long frozenPoints, boolean pass);

    /** 查询可用积分余额 */
    long getBalance(Long userId);
}

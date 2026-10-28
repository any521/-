package com.dianfei.business.carbon;

import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;

/**
 * 账本 B 线内部扩展口（不动 Sprint 0 冻结的 CarbonLedgerService 对外契约）：
 * 风控冻结发分、清零。B4 风控与后台积分调整使用；C/E 仍只依赖 CarbonLedgerService。
 */
public interface CarbonLedgerOps extends CarbonLedgerService {

    /**
     * 扫描识别积分发放（CAM cam04，契约 C6-9）：rule_code=SCAN_CLASSIFY、type=GAIN，
     * 固定积分入账（单次额度由调用方从 sys_config carbon.scan.points 读取），
     * 幂等键 SCAN:{orderId}:GAIN；减排量记 0（识别无重量输入，无法按碳因子折算）。
     * 本方法仍是账本唯一写入路径，不旁路哈希链与账户锁。
     */
    CarbonLedgerResult grantScan(Long userId, Long orderId, long points, String remark);

    /**
     * 环保生态积分发放（LoopFill B5 创意 C1）：签到/成就领取固定积分入账，type=GAIN，
     * rule_code 由调用方传入（ECO_SIGN / ECO_ACHV_{code}），order_type=ECO，
     * 幂等键 ECO:{bizId}:GAIN（bizId 用成就定义 id / 每日签到雪花号）；
     * 减排量记 0（签到无实物减排输入，与 grantScan 一致）。
     * 本方法仍是账本唯一写入路径，不旁路哈希链与账户锁。
     */
    CarbonLedgerResult grantEco(Long userId, Long bizId, long points, String ruleCode, String remark);

    /**
     * 风控冻结发分（B4）：按规则计算积分与减排量，但入 frozen_points 不入可用余额，
     * 流水 type=6 冻结；返回的 logId 作为 biz_risk_alert.freeze_log_id，
     * 复核经 {@link CarbonLedgerService#reviewFrozen} 解冻返还或驳回扣除
     */
    CarbonLedgerResult grantFrozen(CarbonGrantCmd cmd, String reason);

    /** 积分清零（后台处置）：可用余额清零，流水 type=5 */
    CarbonLedgerResult clear(Long userId, String reason);

    /** 手动冻结（后台处置）：可用余额转入 frozen_points，流水 type=6（points 记负出账） */
    CarbonLedgerResult freeze(Long userId, long points, String reason);

    /** 带请求幂等号的人工冻结；requestId 必须由调用方生成并持久化。 */
    default CarbonLedgerResult freeze(Long userId, long points, String reason, String requestId) {
        return freeze(userId, points, reason);
    }

    /** 手动解冻（后台处置）：frozen_points 返还可用余额，流水 type=7（points 记正入账） */
    CarbonLedgerResult unfreeze(Long userId, long points, String reason);

    /** 带请求幂等号的人工解冻；requestId 必须由调用方生成并持久化。 */
    default CarbonLedgerResult unfreeze(Long userId, long points, String reason, String requestId) {
        return unfreeze(userId, points, reason);
    }

    /**
     * 服务费转账（onsite02，契约 C-O2）：用户→工作人员（biz_staff.user_id）原子转账。
     * 幂等键 TRANSFER:{bizType}:{bizId}；两笔流水（转出负/转入正）同批写入，
     * 账户锁 + 哈希链走账本既有机制；余额不足抛 BusinessException 由调用方降级。
     * 转出流水中 bizKey 为 TRANSFER:{bizType}:{bizId}:TRANSFER_OUT、
     * 转入流水为 TRANSFER:{bizType}:{bizId}:TRANSFER_IN（复用账本唯一键幂等）。
     */
    CarbonLedgerResult transfer(Long fromUserId, Long toUserId, long points, String bizType, Long bizId, String remark);
}

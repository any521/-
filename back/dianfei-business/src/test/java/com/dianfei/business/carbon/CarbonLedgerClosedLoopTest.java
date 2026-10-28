package com.dianfei.business.carbon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.dto.CarbonConsumeCmd;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.dto.CarbonRuleSaveDTO;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.entity.CarbonAnchor;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.service.CarbonAnchorService;
import com.dianfei.business.carbon.service.CarbonRuleService;
import com.dianfei.business.carbon.service.CarbonVerifyService;
import com.dianfei.business.carbon.vo.CarbonVerifyVO;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.HashChainUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B2 碳账本闭环单测（H2）：发分规则命中 / 哈希链逐条链接 / 幂等 / 消费与调整 /
 * 冻结复核 / 每日 Merkle 锚定 / 篡改可检测（对齐 trackB 验收标准）
 */
@SpringBootTest
class CarbonLedgerClosedLoopTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(9_000L);

    @Autowired
    private CarbonLedgerOps ledger;
    @Autowired
    private CarbonRuleService ruleService;
    @Autowired
    private CarbonAnchorService anchorService;
    @Autowired
    private CarbonVerifyService verifyService;
    @Autowired
    private CarbonLogMapper logMapper;
    @Autowired
    private CarbonAccountMapper accountMapper;

    @BeforeEach
    void initRules() {
        // upsert 语义，重复执行安全：金属 10 分/kg、0.5 kgCO2/kg；厨余 2 分/袋、0.3 kgCO2/袋
        ruleService.save(rule("RECYCLE_METAL", "RECYCLE", "METAL",
                new BigDecimal("10"), null, new BigDecimal("0.5"), null));
        ruleService.save(rule("KITCHEN_DEFAULT", "KITCHEN", "DEFAULT",
                null, new BigDecimal("2"), null, new BigDecimal("0.3")));
    }

    @Test
    void grantHitsRuleAndChainsHash() {
        long userId = USER_SEQ.incrementAndGet();
        CarbonLedgerResult first = ledger.grant(grantCmd(userId, 101L, new BigDecimal("5.00")));
        assertThat(first.getPoints()).isEqualTo(50);
        assertThat(first.getBalanceAfter()).isEqualTo(50);
        assertThat(first.getCarbonReduced()).isEqualByComparingTo("2.5");
        assertThat(first.getRuleCode()).isEqualTo("RECYCLE_METAL");

        CarbonLedgerResult second = ledger.grant(CarbonGrantCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.KITCHEN).orderId(102L)
                .category("DEFAULT").quantity(3).build());
        assertThat(second.getPoints()).isEqualTo(6);
        assertThat(second.getBalanceAfter()).isEqualTo(56);

        // 哈希链：首条 prev=GENESIS，次条 prev=首条 curr
        List<CarbonLog> logs = accountLogs(userId);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getPrevHash()).isEqualTo(HashChainUtil.GENESIS_HASH);
        assertThat(logs.get(1).getPrevHash()).isEqualTo(logs.get(0).getCurrHash());
        assertThat(HashChainUtil.verify(logs.get(1).getPrevHash(),
                CarbonLedgerServiceImpl.hashPayload(logs.get(1)), logs.get(1).getCurrHash())).isTrue();
    }

    @Test
    void grantIsIdempotentByBizKey() {
        long userId = USER_SEQ.incrementAndGet();
        CarbonLedgerResult first = ledger.grant(grantCmd(userId, 201L, new BigDecimal("2")));
        CarbonLedgerResult replay = ledger.grant(grantCmd(userId, 201L, new BigDecimal("2")));
        assertThat(replay.isDuplicated()).isTrue();
        assertThat(replay.getLogId()).isEqualTo(first.getLogId());
        assertThat(ledger.getBalance(userId)).isEqualTo(20);
        assertThat(accountLogs(userId)).hasSize(1);
    }

    @Test
    void consumeFailsWith2006WhenInsufficient() {
        long userId = USER_SEQ.incrementAndGet();
        assertThatThrownBy(() -> ledger.consume(CarbonConsumeCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.MALL).orderId(301L).points(10).build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.POINTS_NOT_ENOUGH);
    }

    @Test
    void consumeAndAdjustKeepBalanceConsistent() {
        long userId = USER_SEQ.incrementAndGet();
        ledger.grant(grantCmd(userId, 401L, new BigDecimal("10")));   // +100
        ledger.consume(CarbonConsumeCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.MALL).orderId(402L).points(30).build()); // -30
        assertThat(ledger.getBalance(userId)).isEqualTo(70);

        // 补发 +5 / 违规扣减 -20（@OperLog 留痕在 Controller 层）
        ledger.adjust(com.dianfei.business.carbon.dto.CarbonAdjustCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.RECYCLE).orderId(403L)
                .points(5).reason("少称补发").build());
        ledger.adjust(com.dianfei.business.carbon.dto.CarbonAdjustCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.RECYCLE).orderId(404L)
                .points(-20).reason("违规扣减").build());
        assertThat(ledger.getBalance(userId)).isEqualTo(55);
        assertThat(accountLogs(userId)).hasSize(4);
    }

    @Test
    void frozenGrantThenReviewPassAndReject() {
        long userId = USER_SEQ.incrementAndGet();
        // 风控冻结发分：入 frozen_points 不入可用余额
        CarbonLedgerResult frozen = ledger.grantFrozen(CarbonGrantCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.KITCHEN).orderId(501L)
                .category("DEFAULT").quantity(50).build(), "上报量偏离户均");
        assertThat(ledger.getBalance(userId)).isZero();
        CarbonAccount account = findAccount(userId);
        assertThat(account.getFrozenPoints()).isEqualTo(100);

        // 复核通过：解冻返还余额并累计减排量
        ledger.reviewFrozen(userId, frozen.getLogId(), frozen.getPoints(), true);
        assertThat(ledger.getBalance(userId)).isEqualTo(100);
        assertThat(findAccount(userId).getFrozenPoints()).isZero();
        assertThat(findAccount(userId).getTotalCarbonReduced()).isEqualByComparingTo("15.0000");

        // 二次冻结后驳回：冻结积分扣除，余额不变
        CarbonLedgerResult frozen2 = ledger.grantFrozen(CarbonGrantCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.KITCHEN).orderId(502L)
                .category("DEFAULT").quantity(10).build(), "再次异常");
        ledger.reviewFrozen(userId, frozen2.getLogId(), frozen2.getPoints(), false);
        assertThat(ledger.getBalance(userId)).isEqualTo(100);
        assertThat(findAccount(userId).getFrozenPoints()).isZero();
    }

    @Test
    void manualFreezeAndUnfreezeMoveBalance() {
        long userId = USER_SEQ.incrementAndGet();
        ledger.grant(grantCmd(userId, 801L, new BigDecimal("8")));   // +80

        // 手动冻结：可用余额转冻结，流水 type=6 记负
        CarbonLedgerResult freeze = ledger.freeze(userId, 30, "风控处置");
        assertThat(freeze.getBalanceAfter()).isEqualTo(50);
        assertThat(ledger.getBalance(userId)).isEqualTo(50);
        assertThat(findAccount(userId).getFrozenPoints()).isEqualTo(30);

        // 超额冻结/解冻被拒
        assertThatThrownBy(() -> ledger.freeze(userId, 999, "超额"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ledger.unfreeze(userId, 999, "超额"))
                .isInstanceOf(BusinessException.class);

        // 解冻返还：余额恢复，链上可验
        CarbonLedgerResult unfreeze = ledger.unfreeze(userId, 30, "复核通过");
        assertThat(unfreeze.getBalanceAfter()).isEqualTo(80);
        assertThat(findAccount(userId).getFrozenPoints()).isZero();
        List<CarbonLog> logs = accountLogs(userId);
        assertThat(logs).hasSize(3);
        assertThat(logs.get(2).getPrevHash()).isEqualTo(logs.get(1).getCurrHash());
    }

    @Test
    void dailyAnchorThenVerifyToMerkleRoot() {
        long userId = USER_SEQ.incrementAndGet();
        CarbonLedgerResult r1 = ledger.grant(grantCmd(userId, 601L, new BigDecimal("1")));
        CarbonLedgerResult r2 = ledger.grant(grantCmd(userId, 602L, new BigDecimal("3")));

        CarbonAnchor anchor = anchorService.anchorFor(LocalDate.now());
        assertThat(anchor).isNotNull();
        assertThat(anchor.getLeafCount()).isGreaterThanOrEqualTo(2);
        // 幂等：重复锚定返回既有记录
        assertThat(anchorService.anchorFor(LocalDate.now()).getId()).isEqualTo(anchor.getId());

        for (Long logId : List.of(r1.getLogId(), r2.getLogId())) {
            CarbonVerifyVO vo = verifyService.verify(logId);
            assertThat(vo.isHashValid()).isTrue();
            assertThat(vo.isChainLinked()).isTrue();
            assertThat(vo.isAnchored()).isTrue();
            assertThat(vo.getRootMatched()).isTrue();
            assertThat(vo.isValid()).isTrue();
            assertThat(vo.getMerkleRoot()).isEqualTo(anchor.getMerkleRoot());
        }
    }

    @Test
    void tamperedLogIsDetected() {
        long userId = USER_SEQ.incrementAndGet();
        CarbonLedgerResult result = ledger.grant(grantCmd(userId, 701L, new BigDecimal("4")));

        // 篡改流水积分字段（生产环境被仅 INSERT 账号物理阻断，此处模拟绕过后的链上检测）
        CarbonLog row = logMapper.selectById(result.getLogId());
        row.setPoints(row.getPoints() + 1000);
        logMapper.updateById(row);

        CarbonVerifyVO vo = verifyService.verify(result.getLogId());
        assertThat(vo.isHashValid()).isFalse();
        assertThat(vo.isValid()).isFalse();
    }

    // ---------- helpers ----------

    private static CarbonGrantCmd grantCmd(long userId, long orderId, BigDecimal weight) {
        return CarbonGrantCmd.builder()
                .userId(userId).orderType(OrderTypeEnum.RECYCLE).orderId(orderId)
                .category("METAL").weight(weight).build();
    }

    private static CarbonRuleSaveDTO rule(String code, String orderType, String category,
                                          BigDecimal pointsPerKg, BigDecimal pointsPerBag,
                                          BigDecimal carbonPerKg, BigDecimal carbonPerBag) {
        CarbonRuleSaveDTO dto = new CarbonRuleSaveDTO();
        dto.setRuleCode(code);
        dto.setOrderType(orderType);
        dto.setCategory(category);
        dto.setPointsPerKg(pointsPerKg);
        dto.setPointsPerBag(pointsPerBag);
        dto.setCarbonPerKg(carbonPerKg);
        dto.setCarbonPerBag(carbonPerBag);
        dto.setStatus(1);
        return dto;
    }

    private List<CarbonLog> accountLogs(long userId) {
        CarbonAccount account = findAccount(userId);
        return logMapper.selectList(new LambdaQueryWrapper<CarbonLog>()
                .eq(CarbonLog::getAccountId, account.getId())
                .orderByAsc(CarbonLog::getId));
    }

    private CarbonAccount findAccount(long userId) {
        return accountMapper.selectOne(
                new LambdaQueryWrapper<CarbonAccount>().eq(CarbonAccount::getUserId, userId));
    }
}

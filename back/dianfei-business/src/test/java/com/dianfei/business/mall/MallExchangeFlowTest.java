package com.dianfei.business.mall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonAdjustCmd;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.mall.dto.MallExchangeDTO;
import com.dianfei.business.mall.dto.MallProductSaveDTO;
import com.dianfei.business.mall.entity.MallExchange;
import com.dianfei.business.mall.entity.MallProduct;
import com.dianfei.business.mall.service.MallExchangeService;
import com.dianfei.business.mall.service.MallProductService;
import com.dianfei.business.mall.vo.MallExchangeVO;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C3 商城兑换闭环单测（H2 + 真实碳账本）：
 * 成功兑换扣分扣库存 / 余额不足 2006 事务回滚 / 超卖阻止 / 下架拦截 /
 * 取消退分返库存 / 发货完成状态机（对齐 trackC 验收标准）
 */
@SpringBootTest
class MallExchangeFlowTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(80_000L);
    private static final AtomicLong TOPUP_SEQ = new AtomicLong(800_000L);

    @Autowired
    private MallProductService productService;
    @Autowired
    private MallExchangeService exchangeService;
    @Autowired
    private CarbonLedgerOps ledger;
    @Autowired
    private CarbonLogMapper carbonLogMapper;

    @Test
    void exchangeDeductsPointsAndStockInOneTransaction() {
        long userId = topUpUser(1_000);
        MallProduct product = onSaleProduct("环保雨伞", 100, 10);

        MallExchangeVO vo = exchangeService.exchange(userId, exchangeDTO(product.getId(), 2, "1 号楼 101"));

        assertThat(vo.getPointsCost()).isEqualTo(200);
        assertThat(vo.getBalanceAfter()).isEqualTo(800);
        assertThat(vo.getStatus()).isEqualTo(MallExchange.STATUS_EXCHANGED);
        assertThat(vo.getExchangeNo()).startsWith("MO");
        assertThat(ledger.getBalance(userId)).isEqualTo(800);
        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(8);
        // 扣分流水回填溯源：MALL:exchangeId:CONSUME，points 记负
        CarbonLog log = carbonLogMapper.selectById(Long.valueOf(vo.getCarbonLogId()));
        assertThat(log).isNotNull();
        assertThat(log.getPoints()).isEqualTo(-200);
        assertThat(log.getBizKey()).isEqualTo("MALL:" + vo.getId() + ":CONSUME");
    }

    @Test
    void insufficientBalanceReturns2006AndRollsBack() {
        long userId = topUpUser(50);
        MallProduct product = onSaleProduct("积分水杯", 100, 5);

        assertThatThrownBy(() -> exchangeService.exchange(userId, exchangeDTO(product.getId(), 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.POINTS_NOT_ENOUGH);

        // 事务回滚：库存未扣、兑换单未落库、余额不变
        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(5);
        assertThat(exchangeCount(userId)).isZero();
        assertThat(ledger.getBalance(userId)).isEqualTo(50);
    }

    @Test
    void oversellBlockedByConditionalUpdate() {
        long userId = topUpUser(10_000);
        MallProduct product = onSaleProduct("限量帆布袋", 10, 1);

        assertThatThrownBy(() -> exchangeService.exchange(userId, exchangeDTO(product.getId(), 2, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");

        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(1);
        assertThat(exchangeCount(userId)).isZero();
        assertThat(ledger.getBalance(userId)).isEqualTo(10_000);
    }

    @Test
    void offSaleProductRejected() {
        long userId = topUpUser(500);
        MallProduct product = onSaleProduct("已下架好物", 50, 5);
        productService.changeStatus(product.getId(), false);

        assertThatThrownBy(() -> exchangeService.exchange(userId, exchangeDTO(product.getId(), 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下架");
    }

    @Test
    void cancelRefundsPointsAndRestoresStock() {
        long userId = topUpUser(300);
        MallProduct product = onSaleProduct("可降解垃圾袋", 30, 6);
        MallExchangeVO vo = exchangeService.exchange(userId, exchangeDTO(product.getId(), 3, "2 号楼 202"));
        assertThat(ledger.getBalance(userId)).isEqualTo(210);

        MallExchangeVO cancelled = exchangeService.cancel(Long.valueOf(vo.getId()), userId);

        assertThat(cancelled.getStatus()).isEqualTo(MallExchange.STATUS_CANCELLED);
        assertThat(cancelled.getBalanceAfter()).isEqualTo(300);
        assertThat(ledger.getBalance(userId)).isEqualTo(300);
        assertThat(productService.getById(product.getId()).getStock()).isEqualTo(6);
        // 重复取消：状态不允许
        assertThatThrownBy(() -> exchangeService.cancel(Long.valueOf(vo.getId()), userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
    }

    @Test
    void shipAndCompleteFlowBlocksLateCancel() {
        long userId = topUpUser(200);
        MallProduct product = onSaleProduct("再生纸笔记本", 20, 4);
        MallExchangeVO vo = exchangeService.exchange(userId, exchangeDTO(product.getId(), 1, "3 号楼 303"));
        long exchangeId = Long.valueOf(vo.getId());

        exchangeService.ship(exchangeId);
        // 已发货不可取消
        assertThatThrownBy(() -> exchangeService.cancel(exchangeId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
        // 已发货 → 完成
        exchangeService.complete(exchangeId);
        assertThat(exchangeService.detail(exchangeId, userId).getStatus())
                .isEqualTo(MallExchange.STATUS_COMPLETED);
        // 越权访问他人兑换单
        assertThatThrownBy(() -> exchangeService.detail(exchangeId, userId + 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.FORBIDDEN);
    }

    // ---------- 测试辅助 ----------

    /** 初始积分：走账本后台补发（幂等键取唯一充值单号） */
    private long topUpUser(long points) {
        long userId = USER_SEQ.incrementAndGet();
        ledger.adjust(CarbonAdjustCmd.builder()
                .userId(userId)
                .orderType(OrderTypeEnum.MALL)
                .orderId(TOPUP_SEQ.incrementAndGet())
                .points(points)
                .reason("单测初始积分")
                .build());
        return userId;
    }

    private MallProduct onSaleProduct(String name, long pointsPrice, int stock) {
        MallProductSaveDTO dto = new MallProductSaveDTO();
        dto.setProductName(name);
        dto.setPointsPrice(pointsPrice);
        dto.setStock(stock);
        dto.setStatus(MallProduct.STATUS_ON);
        return productService.saveProduct(dto);
    }

    private static MallExchangeDTO exchangeDTO(Long productId, int quantity, String address) {
        MallExchangeDTO dto = new MallExchangeDTO();
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        dto.setAddress(address);
        return dto;
    }

    private long exchangeCount(long userId) {
        return exchangeService.count(new LambdaQueryWrapper<MallExchange>().eq(MallExchange::getUserId, userId));
    }
}

package com.dianfei.business.mall.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerService;
import com.dianfei.business.carbon.dto.CarbonAdjustCmd;
import com.dianfei.business.carbon.dto.CarbonConsumeCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.mall.dto.MallExchangeDTO;
import com.dianfei.business.mall.entity.MallExchange;
import com.dianfei.business.mall.entity.MallProduct;
import com.dianfei.business.mall.mapper.MallExchangeMapper;
import com.dianfei.business.mall.vo.MallExchangeVO;
import com.dianfei.business.message.MessageSender;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分兑换（C3）：校验余额→扣分→扣库存→生成兑换单，全程单事务；
 * 余额不足由碳账本抛 2006；超卖由 deductStock 条件更新阻止；
 * 取消（仅待发货）：积分退回（账本 adjust 补发）+ 库存返还。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallExchangeService extends ServiceImpl<MallExchangeMapper, MallExchange> {

    private final MallProductService mallProductService;
    private final CarbonLedgerService carbonLedgerService;
    private final MessageSender messageSender;

    /**
     * 兑换下单：兑换单先落库取 ID 作为账本幂等键（userId:MALL:exchangeId:CONSUME），
     * 再条件更新扣库存（防超卖），最后扣分并回填流水 ID；任一步失败整体回滚
     */
    @Transactional
    public MallExchangeVO exchange(Long userId, MallExchangeDTO dto) {
        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (quantity <= 0 || quantity > 1000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "兑换数量必须在 1 到 1000 范围内");
        }
        if (dto.getAddress() != null && dto.getAddress().trim().length() > 255) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收货地址长度不能超过 255 个字符");
        }
        MallProduct product = mallProductService.requireOnSale(dto.getProductId());
        long pointsCost = product.getPointsPrice() * quantity;

        MallExchange exchange = new MallExchange();
        exchange.setExchangeNo("MO" + IdUtil.getSnowflakeNextIdStr());
        exchange.setUserId(userId);
        exchange.setProductId(product.getId());
        exchange.setProductName(product.getProductName());
        exchange.setPointsCost(pointsCost);
        exchange.setQuantity(quantity);
        exchange.setStatus(MallExchange.STATUS_EXCHANGED);
        exchange.setAddress(dto.getAddress());
        save(exchange);

        if (mallProductService.getBaseMapper().deductStock(product.getId(), quantity) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "库存不足或商品已下架，兑换失败");
        }
        CarbonLedgerResult ledger = carbonLedgerService.consume(CarbonConsumeCmd.builder()
                .userId(userId)
                .orderType(OrderTypeEnum.MALL)
                .orderId(exchange.getId())
                .points(pointsCost)
                .remark("商城兑换 " + product.getProductName() + " x" + quantity)
                .build());
        exchange.setCarbonLogId(ledger.getLogId());
        updateById(exchange);

        messageSender.send(userId, MessageSender.TYPE_ORDER_FINISHED,
                "兑换成功", "已兑换 " + product.getProductName() + " x" + quantity
                        + "，扣减 " + pointsCost + " 积分，当前余额 " + ledger.getBalanceAfter());
        return toVO(exchange, ledger.getBalanceAfter());
    }

    /** 用户取消（仅 0待发货）：账本补发退分 + 库存返还，同事务 */
    @Transactional
    public MallExchangeVO cancel(Long exchangeId, Long userId) {
        MallExchange exchange = requireExchange(exchangeId);
        if (!exchange.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (baseMapper.cancelIfPending(exchangeId, userId, MallExchange.STATUS_EXCHANGED,
                MallExchange.STATUS_CANCELLED) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "已发货/已完成或已取消的兑换单不可取消");
        }
        CarbonLedgerResult ledger = carbonLedgerService.adjust(CarbonAdjustCmd.builder()
                .userId(userId)
                .orderType(OrderTypeEnum.MALL)
                .orderId(exchange.getId())
                .points(exchange.getPointsCost())
                .reason("取消兑换退回：" + exchange.getExchangeNo())
                .build());
        mallProductService.getBaseMapper().restoreStock(exchange.getProductId(), exchange.getQuantity());
        exchange.setStatus(MallExchange.STATUS_CANCELLED);
        messageSender.send(userId, MessageSender.TYPE_POINTS_ARRIVED,
                "兑换已取消", "兑换单 " + exchange.getExchangeNo() + " 已取消，退回 "
                        + exchange.getPointsCost() + " 积分，当前余额 " + ledger.getBalanceAfter());
        return toVO(exchange, ledger.getBalanceAfter());
    }

    /** 用户端：我的兑换单分页 */
    public PageVO<MallExchangeVO> pageUserExchanges(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<MallExchange> wrapper = new LambdaQueryWrapper<MallExchange>()
                .eq(MallExchange::getUserId, userId)
                .eq(status != null, MallExchange::getStatus, status)
                .orderByDesc(MallExchange::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper).convert(e -> toVO(e, null)));
    }

    /** 后台：兑换单分页（可按用户/商品/状态过滤） */
    public PageVO<MallExchangeVO> pageAdmin(Integer status, Long userId, Long productId, int pageNum, int pageSize) {
        LambdaQueryWrapper<MallExchange> wrapper = new LambdaQueryWrapper<MallExchange>()
                .eq(status != null, MallExchange::getStatus, status)
                .eq(userId != null, MallExchange::getUserId, userId)
                .eq(productId != null, MallExchange::getProductId, productId)
                .orderByDesc(MallExchange::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper).convert(e -> toVO(e, null)));
    }

    /** 后台发货：0已兑换 → 1已发货 */
    @Transactional
    public void ship(Long exchangeId) {
        MallExchange exchange = requireExchange(exchangeId);
        requireStatus(exchange, MallExchange.STATUS_EXCHANGED, "仅待发货兑换单可发货");
        exchange.setStatus(MallExchange.STATUS_SHIPPED);
        updateById(exchange);
        messageSender.send(exchange.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                "兑换商品已发货", "兑换单 " + exchange.getExchangeNo() + " 已发货，请注意查收");
    }

    /** 后台完成：1已发货 → 2已完成 */
    @Transactional
    public void complete(Long exchangeId) {
        MallExchange exchange = requireExchange(exchangeId);
        requireStatus(exchange, MallExchange.STATUS_SHIPPED, "仅已发货兑换单可完成");
        exchange.setStatus(MallExchange.STATUS_COMPLETED);
        updateById(exchange);
    }

    /** 兑换单详情（userId 非空时校验归属） */
    public MallExchangeVO detail(Long exchangeId, Long userId) {
        MallExchange exchange = requireExchange(exchangeId);
        if (userId != null && !exchange.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return toVO(exchange, null);
    }

    public MallExchange requireExchange(Long exchangeId) {
        MallExchange exchange = getById(exchangeId);
        if (exchange == null) {
            throw new BusinessException("兑换单不存在");
        }
        return exchange;
    }

    private static void requireStatus(MallExchange exchange, int expected, String message) {
        if (exchange.getStatus() != expected) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, message);
        }
    }

    public static String statusDesc(Integer status) {
        return switch (status) {
            case MallExchange.STATUS_EXCHANGED -> "已兑换";
            case MallExchange.STATUS_SHIPPED -> "已发货";
            case MallExchange.STATUS_COMPLETED -> "已完成";
            case MallExchange.STATUS_CANCELLED -> "已取消";
            default -> String.valueOf(status);
        };
    }

    private static MallExchangeVO toVO(MallExchange exchange, Long balanceAfter) {
        MallExchangeVO vo = new MallExchangeVO();
        vo.setId(String.valueOf(exchange.getId()));
        vo.setExchangeNo(exchange.getExchangeNo());
        vo.setProductId(String.valueOf(exchange.getProductId()));
        vo.setProductName(exchange.getProductName());
        vo.setPointsCost(exchange.getPointsCost());
        vo.setQuantity(exchange.getQuantity());
        vo.setCarbonLogId(exchange.getCarbonLogId() == null ? null : String.valueOf(exchange.getCarbonLogId()));
        vo.setStatus(exchange.getStatus());
        vo.setStatusDesc(statusDesc(exchange.getStatus()));
        vo.setAddress(exchange.getAddress());
        vo.setBalanceAfter(balanceAfter);
        vo.setCreateTime(exchange.getCreateTime());
        return vo;
    }
}

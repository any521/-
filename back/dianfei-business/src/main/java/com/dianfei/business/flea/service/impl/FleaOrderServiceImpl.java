package com.dianfei.business.flea.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.flea.dto.FleaOrderCreateDTO;
import com.dianfei.business.flea.dto.FleaReviewDTO;
import com.dianfei.business.flea.entity.FleaItem;
import com.dianfei.business.flea.entity.FleaOrder;
import com.dianfei.business.flea.mapper.FleaOrderMapper;
import com.dianfei.business.flea.service.FleaItemService;
import com.dianfei.business.flea.service.FleaOrderService;
import com.dianfei.business.flea.vo.FleaOrderVO;
import com.dianfei.business.message.MessageSender;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 跳蚤订单域（Upgrade U3）：积分托管结算——
 * 下单 CarbonLedgerOps.freeze 冻结（freeze_log_id 留痕，取消凭据）、
 * 收货 transfer 原子转账（幂等键 TRANSFER:FLEA:{orderId}，两笔流水同批入链）、
 * 取消 unfreeze 退冻；防双卖由商品条件更新保证，任一步失败整体回滚，无旁路账本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FleaOrderServiceImpl extends ServiceImpl<FleaOrderMapper, FleaOrder> implements FleaOrderService {

    private final FleaItemService fleaItemService;
    private final CarbonLedgerOps carbonLedgerOps;
    private final SysUserMapper sysUserMapper;
    private final MessageSender messageSender;

    @Override
    @Transactional
    public FleaOrderVO create(Long userId, FleaOrderCreateDTO dto) {
        FleaItem item = fleaItemService.requireOnSale(dto.getItemId());
        if (item.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能购买自己发布的商品");
        }
        if (carbonLedgerOps.getBalance(userId) < item.getPrice()) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH, "碳账户余额不足，请先积攒积分");
        }
        String orderNo = "FO" + IdUtil.getSnowflakeNextIdStr();
        // 1. 冻结买家积分（流水入链，freeze_log_id 作为取消解冻凭据）
        CarbonLedgerResult frozen = carbonLedgerOps.freeze(userId, item.getPrice(), "FLEA_ORDER_" + orderNo);
        // 2. 写订单（status=0 待发货）
        FleaOrder order = new FleaOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setSellerId(item.getUserId());
        order.setItemId(item.getId());
        order.setItemTitle(item.getTitle());
        order.setItemImage(item.getImageUrl());
        order.setPrice(item.getPrice());
        order.setAddress(dto.getAddress().trim());
        order.setStatus(FleaOrder.STATUS_PENDING_SHIP);
        order.setFreezeLogId(frozen.getLogId());
        save(order);
        // 3. 商品防双卖条件更新（0 行抛异常整体回滚，含冻结流水）
        fleaItemService.markSold(item.getId());
        // 4. 通知卖家发货
        messageSender.send(item.getUserId(), MessageSender.TYPE_FLEA_NEW_ORDER,
                "您有新的跳蚤订单", "商品「" + item.getTitle() + "」已被下单，请及时发货");
        return toVO(order, null, null, item.getPrice());
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long userId) {
        FleaOrder order = requireOrder(orderId);
        requireBuyer(order, userId);
        if (baseMapper.cancelIfPending(orderId, userId, FleaOrder.STATUS_PENDING_SHIP,
                FleaOrder.STATUS_CANCELLED) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "仅待发货订单可取消，订单可能已被处理");
        }
        carbonLedgerOps.unfreeze(userId, order.getPrice(), "FLEA_ORDER_" + order.getOrderNo() + "_CANCEL");
        fleaItemService.backOnSale(order.getItemId());
        messageSender.send(userId, MessageSender.TYPE_FLEA_CANCELLED,
                "订单已取消", "订单 " + order.getOrderNo() + " 已取消，" + order.getPrice() + " 积分已退回");
    }

    @Override
    @Transactional
    public void ship(Long orderId, Long userId) {
        FleaOrder order = requireOrder(orderId);
        requireSeller(order, userId);
        requireStatus(order, FleaOrder.STATUS_PENDING_SHIP, "仅待发货订单可发货");
        order.setStatus(FleaOrder.STATUS_SHIPPED);
        order.setShipTime(LocalDateTime.now());
        updateById(order);
        messageSender.send(order.getUserId(), MessageSender.TYPE_FLEA_SHIPPED,
                "订单已发货", "您购买的「" + order.getItemTitle() + "」已发货，预计 3-5 天送达，请留意查收");
    }

    @Override
    @Transactional
    public void receive(Long orderId, Long userId) {
        FleaOrder order = requireOrder(orderId);
        requireBuyer(order, userId);
        requireStatus(order, FleaOrder.STATUS_SHIPPED, "仅待收货订单可确认收货");
        // 原子转账：买家 → 卖家，幂等键 TRANSFER:FLEA:{orderId}，两笔流水同批入链
        carbonLedgerOps.transfer(userId, order.getSellerId(), order.getPrice(),
                "FLEA", order.getId(), "跳蚤订单结算：" + order.getOrderNo());
        order.setStatus(FleaOrder.STATUS_FINISHED);
        order.setReceiveTime(LocalDateTime.now());
        updateById(order);
        messageSender.send(order.getSellerId(), MessageSender.TYPE_FLEA_FINISHED,
                "订单已完成", "订单 " + order.getOrderNo() + " 买家已确认收货，" + order.getPrice() + " 积分已到账");
    }

    @Override
    @Transactional
    public void review(Long orderId, Long userId, FleaReviewDTO dto) {
        FleaOrder order = requireOrder(orderId);
        requireBuyer(order, userId);
        requireStatus(order, FleaOrder.STATUS_FINISHED, "仅已完成订单可评价");
        if (order.getRating() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该订单已评价");
        }
        order.setRating(dto.getRating());
        order.setComment(dto.getComment() == null ? null : dto.getComment().trim());
        updateById(order);
        messageSender.send(order.getSellerId(), MessageSender.TYPE_FLEA_REVIEWED,
                "收到新评价", "订单 " + order.getOrderNo() + " 收到买家 " + dto.getRating() + " 星评价");
    }

    @Override
    public PageVO<FleaOrderVO> pageBuyer(Long userId, Integer status, int pageNum, int pageSize) {
        return pageOrders(new LambdaQueryWrapper<FleaOrder>()
                .eq(FleaOrder::getUserId, userId)
                .eq(status != null, FleaOrder::getStatus, status), pageNum, pageSize);
    }

    @Override
    public PageVO<FleaOrderVO> pageSeller(Long userId, Integer status, int pageNum, int pageSize) {
        return pageOrders(new LambdaQueryWrapper<FleaOrder>()
                .eq(FleaOrder::getSellerId, userId)
                .eq(status != null, FleaOrder::getStatus, status), pageNum, pageSize);
    }

    private PageVO<FleaOrderVO> pageOrders(LambdaQueryWrapper<FleaOrder> wrapper, int pageNum, int pageSize) {
        Page<FleaOrder> p = page(new Page<>(pageNum, pageSize), wrapper.orderByDesc(FleaOrder::getId));
        List<FleaOrder> rows = p.getRecords();
        if (rows.isEmpty()) {
            return PageVO.of(p.getTotal(), p.getPages(), List.of());
        }
        Set<Long> userIds = new HashSet<>();
        rows.forEach(o -> {
            userIds.add(o.getUserId());
            userIds.add(o.getSellerId());
        });
        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        List<FleaOrderVO> vos = rows.stream()
                .map(o -> toVO(o, users.get(o.getUserId()), users.get(o.getSellerId()), null))
                .toList();
        return PageVO.of(p.getTotal(), p.getPages(), vos);
    }

    private FleaOrder requireOrder(Long orderId) {
        FleaOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单不存在");
        }
        return order;
    }

    private static void requireBuyer(FleaOrder order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private static void requireSeller(FleaOrder order, Long userId) {
        if (!order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private static void requireStatus(FleaOrder order, int expected, String message) {
        if (order.getStatus() != expected) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, message);
        }
    }

    private static FleaOrderVO toVO(FleaOrder order, SysUser buyer, SysUser seller, Long frozenPoints) {
        return FleaOrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .itemId(order.getItemId())
                .itemTitle(order.getItemTitle())
                .itemImage(order.getItemImage())
                .price(order.getPrice())
                .address(order.getAddress())
                .status(order.getStatus())
                .statusDesc(statusDesc(order.getStatus()))
                .rating(order.getRating())
                .comment(order.getComment())
                .buyerId(order.getUserId())
                .buyerNickname(buyer == null ? "用户" : nickname(buyer))
                .buyerAvatar(buyer == null ? null : buyer.getAvatar())
                .sellerId(order.getSellerId())
                .sellerNickname(seller == null ? "用户" : nickname(seller))
                .sellerAvatar(seller == null ? null : seller.getAvatar())
                .freezeLogId(order.getFreezeLogId())
                .frozenPoints(frozenPoints)
                .createTime(order.getCreateTime())
                .shipTime(order.getShipTime())
                .receiveTime(order.getReceiveTime())
                .cancelTime(order.getCancelTime())
                .build();
    }

    private static String nickname(SysUser user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    public static String statusDesc(Integer status) {
        return switch (status) {
            case FleaOrder.STATUS_PENDING_SHIP -> "待发货";
            case FleaOrder.STATUS_SHIPPED -> "待收货";
            case FleaOrder.STATUS_FINISHED -> "已完成";
            case FleaOrder.STATUS_CANCELLED -> "已取消";
            default -> String.valueOf(status);
        };
    }
}

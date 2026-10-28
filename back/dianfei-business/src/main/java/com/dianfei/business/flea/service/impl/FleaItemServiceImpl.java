package com.dianfei.business.flea.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.flea.dto.FleaItemCreateDTO;
import com.dianfei.business.flea.entity.FleaItem;
import com.dianfei.business.flea.mapper.FleaItemMapper;
import com.dianfei.business.flea.service.FleaItemService;
import com.dianfei.business.flea.vo.FleaItemVO;
import com.dianfei.business.flea.vo.FleaStatsVO;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 闲置商品域（Upgrade U3）：发布/在售浏览/详情/本人下架 + 管理端全量/违规下架；
 * 防双卖由 Mapper 条件更新（WHERE status=1）保证，订单域 markSold/backOnSale 驱动状态流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FleaItemServiceImpl extends ServiceImpl<FleaItemMapper, FleaItem> implements FleaItemService {

    /** 合法分类枚举（对齐 V29 category 注释） */
    public static final Set<String> CATEGORIES = Set.of("REUSABLE", "TOY", "BOOK", "ELECTRONIC", "HOME", "OTHER");

    private final SysUserMapper sysUserMapper;
    private final MessageSender messageSender;

    @Override
    @Transactional
    public FleaItemVO create(Long userId, FleaItemCreateDTO dto) {
        String category = dto.getCategory().trim().toUpperCase();
        if (!CATEGORIES.contains(category)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "分类不合法（REUSABLE/TOY/BOOK/ELECTRONIC/HOME/OTHER）");
        }
        String imageUrl = dto.getImageUrl();
        if (imageUrl != null && imageUrl.isBlank()) {
            imageUrl = null;
        }
        if (imageUrl != null && !imageUrl.startsWith("/api/file/preview/")
                && !imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片地址不合法");
        }
        FleaItem item = new FleaItem();
        item.setUserId(userId);
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategory(category);
        item.setPrice(dto.getPrice());
        item.setImageUrl(imageUrl);
        item.setStatus(FleaItem.STATUS_ON_SALE);
        save(item);
        return toVO(item, null, null, null);
    }

    @Override
    public PageVO<FleaItemVO> page(String category, String keyword, Long minPrice, Long maxPrice,
                                   int pageNum, int pageSize) {
        // 先归一化再入条件（实参无条件短路，null 直接进 .eq 实参会 NPE）
        String cat = category == null || category.isBlank() ? null : category.trim().toUpperCase();
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        LambdaQueryWrapper<FleaItem> wrapper = new LambdaQueryWrapper<FleaItem>()
                .eq(FleaItem::getStatus, FleaItem.STATUS_ON_SALE)
                .eq(cat != null, FleaItem::getCategory, cat)
                .like(kw != null, FleaItem::getTitle, kw)
                .ge(minPrice != null, FleaItem::getPrice, minPrice)
                .le(maxPrice != null, FleaItem::getPrice, maxPrice)
                .orderByDesc(FleaItem::getCreateTime);
        Page<FleaItem> p = page(new Page<>(pageNum, pageSize), wrapper);
        return toVOPage(p);
    }

    @Override
    public FleaItemVO detail(Long id) {
        FleaItem item = getById(id);
        if (item == null || item.getStatus() == FleaItem.STATUS_FORBIDDEN) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "商品不存在");
        }
        SysUser seller = sysUserMapper.selectById(item.getUserId());
        long soldCount = soldCountOf(item.getUserId());
        FleaStatsVO stats = FleaStatsVO.builder()
                .onSaleCount(count(new LambdaQueryWrapper<FleaItem>()
                        .eq(FleaItem::getUserId, item.getUserId())
                        .eq(FleaItem::getStatus, FleaItem.STATUS_ON_SALE)))
                .soldCount(soldCount)
                .build();
        return toVO(item, seller, soldCount, stats);
    }

    @Override
    @Transactional
    public void off(Long id, Long userId) {
        FleaItem item = requireItem(id);
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (item.getStatus() != FleaItem.STATUS_ON_SALE) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "仅可在售商品下架");
        }
        item.setStatus(FleaItem.STATUS_OFF);
        updateById(item);
    }

    @Override
    public PageVO<FleaItemVO> minePage(Long userId, int pageNum, int pageSize) {
        Page<FleaItem> p = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FleaItem>()
                        .eq(FleaItem::getUserId, userId)
                        .orderByDesc(FleaItem::getCreateTime));
        return toVOPage(p);
    }

    @Override
    public PageVO<FleaItemVO> pageOps(String keyword, Long userId, int pageNum, int pageSize) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        LambdaQueryWrapper<FleaItem> wrapper = new LambdaQueryWrapper<FleaItem>()
                .eq(userId != null, FleaItem::getUserId, userId)
                .like(kw != null, FleaItem::getTitle, kw)
                .orderByDesc(FleaItem::getCreateTime);
        return toVOPage(page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    @Transactional
    public void forbidden(Long id) {
        FleaItem item = requireItem(id);
        if (item.getStatus() == FleaItem.STATUS_FORBIDDEN) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "商品已违规下架");
        }
        item.setStatus(FleaItem.STATUS_FORBIDDEN);
        updateById(item);
        messageSender.send(item.getUserId(), MessageSender.TYPE_ITEM_OFF_SHELF,
                "商品违规下架", "您的商品「" + item.getTitle() + "」因内容违规已被平台下架，如有疑问请联系客服");
    }

    @Override
    @Transactional
    public void markSold(Long itemId) {
        if (baseMapper.markSold(itemId, FleaItem.STATUS_ON_SALE, FleaItem.STATUS_SOLD) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "商品已被下单或已下架，请刷新重试");
        }
    }

    @Override
    public void backOnSale(Long itemId) {
        if (baseMapper.backOnSale(itemId, FleaItem.STATUS_ON_SALE, FleaItem.STATUS_SOLD) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                    "商品状态已变化，无法安全回售");
        }
    }

    @Override
    public FleaItem requireOnSale(Long itemId) {
        FleaItem item = getById(itemId);
        if (item == null || item.getStatus() != FleaItem.STATUS_ON_SALE) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "商品不存在或已下架");
        }
        return item;
    }

    private FleaItem requireItem(Long id) {
        FleaItem item = getById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "商品不存在");
        }
        return item;
    }

    /** 分页行批量回填卖家昵称/头像 + soldCount */
    private PageVO<FleaItemVO> toVOPage(Page<FleaItem> p) {
        List<FleaItem> rows = p.getRecords();
        if (rows.isEmpty()) {
            return PageVO.of(p.getTotal(), p.getPages(), List.of());
        }
        Set<Long> sellerIds = rows.stream().map(FleaItem::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> sellers = sellerMap(sellerIds);
        Map<Long, Long> soldCounts = soldCountMap(sellerIds);
        List<FleaItemVO> vos = rows.stream()
                .map(item -> toVO(item, sellers.get(item.getUserId()),
                        soldCounts.getOrDefault(item.getUserId(), 0L), null))
                .toList();
        return PageVO.of(p.getTotal(), p.getPages(), vos);
    }

    private FleaItemVO toVO(FleaItem item, SysUser seller, Long soldCount, FleaStatsVO stats) {
        return FleaItemVO.builder()
                .id(item.getId())
                .userId(item.getUserId())
                .sellerNickname(seller == null ? "用户" : nickname(seller))
                .sellerAvatar(seller == null ? null : seller.getAvatar())
                .title(item.getTitle())
                .description(item.getDescription())
                .category(item.getCategory())
                .price(item.getPrice())
                .imageUrl(item.getImageUrl())
                .status(item.getStatus())
                .statusDesc(statusDesc(item.getStatus()))
                .soldCount(soldCount)
                .sellerStats(stats)
                .createTime(item.getCreateTime())
                .build();
    }

    private Map<Long, SysUser> sellerMap(Collection<Long> sellerIds) {
        if (sellerIds == null || sellerIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(sellerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    /** 卖家已售出数（status=2 简单聚合） */
    private Map<Long, Long> soldCountMap(Collection<Long> sellerIds) {
        if (sellerIds == null || sellerIds.isEmpty()) {
            return Map.of();
        }
        return baseMapper.selectSoldCountBySellers(sellerIds, FleaItem.STATUS_SOLD).stream()
                .collect(Collectors.toMap(FleaItemMapper.SoldCount::getUserId,
                        FleaItemMapper.SoldCount::getCnt, (a, b) -> a));
    }

    private long soldCountOf(Long userId) {
        return soldCountMap(Set.of(userId)).getOrDefault(userId, 0L);
    }

    private static String nickname(SysUser user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    public static String statusDesc(Integer status) {
        return switch (status) {
            case FleaItem.STATUS_OFF -> "已下架";
            case FleaItem.STATUS_ON_SALE -> "在售";
            case FleaItem.STATUS_SOLD -> "已售出";
            case FleaItem.STATUS_FORBIDDEN -> "违规下架";
            default -> String.valueOf(status);
        };
    }
}

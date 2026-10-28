package com.dianfei.business.badge.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.badge.dto.BadgeSaveDTO;
import com.dianfei.business.badge.entity.BizBadge;
import com.dianfei.business.badge.entity.BizBadgeExchange;
import com.dianfei.business.badge.mapper.BizBadgeExchangeMapper;
import com.dianfei.business.badge.mapper.BizBadgeMapper;
import com.dianfei.business.badge.service.BadgeService;
import com.dianfei.business.badge.vo.BadgeExchangeVO;
import com.dianfei.business.badge.vo.BadgeVO;
import com.dianfei.business.carbon.CarbonLedgerService;
import com.dianfei.business.carbon.dto.CarbonConsumeCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.message.MessageSender;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 徽章域实现（积分市场合并升级）：兑换流程对齐 MallExchangeService——
 * 兑换单先落库取 ID 作为账本幂等键（BADGE:{exchangeId}:CONSUME），再扣分回填流水；
 * 防重复兑换由唯一索引 uk(user_id, badge_id) 兜底，冲突转业务异常提示已拥有。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeServiceImpl extends ServiceImpl<BizBadgeMapper, BizBadge> implements BadgeService {

    private final BizBadgeExchangeMapper exchangeMapper;
    private final CarbonLedgerService carbonLedgerService;
    private final MessageSender messageSender;

    @Override
    public PageVO<BadgeVO> pageOnSale(Long userId, String keyword, int pageNum, int pageSize) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<BizBadge> p = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizBadge>()
                        .eq(BizBadge::getStatus, BizBadge.STATUS_ON)
                        .like(kw != null, BizBadge::getName, kw)
                        .orderByAsc(BizBadge::getSort)
                        .orderByAsc(BizBadge::getId));
        List<BizBadge> rows = p.getRecords();
        if (rows.isEmpty()) {
            return PageVO.of(p.getTotal(), p.getPages(), List.of());
        }
        Set<Long> ownedIds = userId == null ? Set.of() : ownedBadgeIds(userId, rows);
        List<BadgeVO> vos = rows.stream()
                .map(b -> toVO(b, userId != null && ownedIds.contains(b.getId())))
                .toList();
        return PageVO.of(p.getTotal(), p.getPages(), vos);
    }

    @Override
    @Transactional
    public BadgeExchangeVO exchange(Long userId, Long badgeId) {
        BizBadge badge = requireOnSale(badgeId);
        if (exchangeMapper.selectCount(new LambdaQueryWrapper<BizBadgeExchange>()
                .eq(BizBadgeExchange::getUserId, userId)
                .eq(BizBadgeExchange::getBadgeId, badgeId)) > 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "您已拥有该徽章，无需重复兑换");
        }
        BizBadgeExchange exchange = new BizBadgeExchange();
        exchange.setExchangeNo("BE" + IdUtil.getSnowflakeNextIdStr());
        exchange.setUserId(userId);
        exchange.setBadgeId(badge.getId());
        exchange.setBadgeName(badge.getName());
        exchange.setBadgeIcon(badge.getIconUrl());
        exchange.setPointsCost(badge.getPrice());
        exchange.setStatus(BizBadgeExchange.STATUS_EXCHANGED);
        try {
            exchangeMapper.insert(exchange);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "您已拥有该徽章，无需重复兑换");
        }
        CarbonLedgerResult ledger = carbonLedgerService.consume(CarbonConsumeCmd.builder()
                .userId(userId)
                .orderType(OrderTypeEnum.BADGE)
                .orderId(exchange.getId())
                .points(badge.getPrice())
                .remark("徽章兑换 " + badge.getName())
                .build());
        exchange.setCarbonLogId(ledger.getLogId());
        exchangeMapper.updateById(exchange);

        messageSender.send(userId, MessageSender.TYPE_ORDER_FINISHED,
                "徽章兑换成功", "已获得「" + badge.getName() + "」徽章，扣减 "
                        + badge.getPrice() + " 积分，当前余额 " + ledger.getBalanceAfter());
        return toExchangeVO(exchange, ledger.getBalanceAfter(), badge.getCode());
    }

    @Override
    public PageVO<BadgeExchangeVO> pageMine(Long userId, int pageNum, int pageSize) {
        Page<BizBadgeExchange> p = exchangeMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizBadgeExchange>()
                        .eq(BizBadgeExchange::getUserId, userId)
                        .orderByDesc(BizBadgeExchange::getCreateTime));
        // 批量查徽章定义，回填编码快照（前端按 code 匹配内置 SVG 图标）
        Map<Long, String> codeById = p.getRecords().isEmpty() ? Map.of() : listByIds(
                        p.getRecords().stream().map(BizBadgeExchange::getBadgeId).distinct().toList())
                .stream().collect(Collectors.toMap(BizBadge::getId, BizBadge::getCode));
        return PageVO.of(p.getTotal(), p.getPages(),
                p.getRecords().stream().map(e -> toExchangeVO(e, null, codeById.get(e.getBadgeId()))).toList());
    }

    @Override
    public PageVO<BadgeVO> pageAdmin(String keyword, Integer status, int pageNum, int pageSize) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<BizBadge> p = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizBadge>()
                        .eq(status != null, BizBadge::getStatus, status)
                        .and(kw != null, w -> w.like(BizBadge::getName, kw).or().like(BizBadge::getCode, kw))
                        .orderByAsc(BizBadge::getSort)
                        .orderByAsc(BizBadge::getId));
        return PageVO.of(p.getTotal(), p.getPages(),
                p.getRecords().stream().map(b -> toVO(b, null)).toList());
    }

    @Override
    @Transactional
    public BadgeVO save(BadgeSaveDTO dto) {
        String code = dto.getCode().trim().toUpperCase();
        requireCodeUnique(code, dto.getId());
        String iconUrl = dto.getIconUrl();
        if (iconUrl != null && iconUrl.isBlank()) {
            iconUrl = null;
        }
        if (iconUrl != null && !iconUrl.startsWith("/api/file/preview/")
                && !iconUrl.startsWith("http://") && !iconUrl.startsWith("https://")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图标地址不合法");
        }
        BizBadge badge = dto.getId() == null ? new BizBadge() : requireBadge(dto.getId());
        badge.setCode(code);
        badge.setName(dto.getName().trim());
        badge.setDescription(dto.getDescription().trim());
        badge.setPrice(dto.getPrice());
        badge.setIconUrl(iconUrl);
        badge.setSort(dto.getSort() == null ? 0 : dto.getSort());
        badge.setStatus(dto.getStatus());
        saveOrUpdate(badge);
        return toVO(badge, null);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean onSale) {
        BizBadge badge = requireBadge(id);
        badge.setStatus(onSale ? BizBadge.STATUS_ON : BizBadge.STATUS_OFF);
        updateById(badge);
    }

    /** 上架徽章校验：不存在/已下架均报错 */
    private BizBadge requireOnSale(Long badgeId) {
        BizBadge badge = getById(badgeId);
        if (badge == null || badge.getStatus() != BizBadge.STATUS_ON) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "徽章不存在或已下架");
        }
        return badge;
    }

    private BizBadge requireBadge(Long id) {
        BizBadge badge = getById(id);
        if (badge == null) {
            throw new BusinessException("徽章不存在");
        }
        return badge;
    }

    /** code 唯一校验（编辑排除自身） */
    private void requireCodeUnique(String code, Long selfId) {
        Long dup = getBaseMapper().selectCount(new LambdaQueryWrapper<BizBadge>()
                .eq(BizBadge::getCode, code)
                .ne(selfId != null, BizBadge::getId, selfId));
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "徽章编码已存在：" + code);
        }
    }

    /** 当前用户已兑换的徽章 ID 集合（列表回填 owned） */
    private Set<Long> ownedBadgeIds(Long userId, List<BizBadge> rows) {
        Set<Long> badgeIds = rows.stream().map(BizBadge::getId).collect(Collectors.toSet());
        return exchangeMapper.selectList(new LambdaQueryWrapper<BizBadgeExchange>()
                        .eq(BizBadgeExchange::getUserId, userId)
                        .in(BizBadgeExchange::getBadgeId, badgeIds))
                .stream().map(BizBadgeExchange::getBadgeId).collect(Collectors.toSet());
    }

    private static BadgeVO toVO(BizBadge badge, Boolean owned) {
        return BadgeVO.builder()
                .id(badge.getId())
                .code(badge.getCode())
                .name(badge.getName())
                .description(badge.getDescription())
                .price(badge.getPrice())
                .iconUrl(badge.getIconUrl())
                .sort(badge.getSort())
                .status(badge.getStatus())
                .owned(owned)
                .build();
    }

    private static BadgeExchangeVO toExchangeVO(BizBadgeExchange e, Long balanceAfter, String badgeCode) {
        BadgeExchangeVO vo = new BadgeExchangeVO();
        vo.setId(e.getId());
        vo.setExchangeNo(e.getExchangeNo());
        vo.setBadgeId(e.getBadgeId());
        vo.setBadgeName(e.getBadgeName());
        vo.setBadgeCode(badgeCode);
        vo.setBadgeIcon(e.getBadgeIcon());
        vo.setPointsCost(e.getPointsCost());
        vo.setStatus(e.getStatus());
        vo.setBalanceAfter(balanceAfter);
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}

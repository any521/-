package com.dianfei.business.evaluate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.evaluate.dto.ReviewCreateDTO;
import com.dianfei.business.evaluate.entity.OrderReview;
import com.dianfei.business.evaluate.mapper.OrderReviewMapper;
import com.dianfei.business.evaluate.vo.ReviewCheckVO;
import com.dianfei.business.evaluate.vo.ReviewVO;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.service.KitchenReportService;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.service.RepairOrderService;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.enums.RepairStatusEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单评价（GapFill B2）：用户对已完成订单提交评价（每单每人一次，uk_review 防重复），
 * 我的评价分页连订单号展示；staff_id 随单落库供员工绩效好评率聚合。
 * 状态校验映射：RECYCLE→3 已完成；REPAIR→3 已完成；KITCHEN→非风控冻结（0 正常/2 已复核，积分已发放）
 * —— 契约注明「KITCHEN→status=1 已归档」与实体语义冲突（1 为风控冻结待复核），实现按实体语义收紧。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewService extends ServiceImpl<OrderReviewMapper, OrderReview> {

    /** 可评价订单类型（MALL 无服务员工，不在评价范围） */
    private static final Set<OrderTypeEnum> REVIEWABLE = EnumSet.of(
            OrderTypeEnum.RECYCLE, OrderTypeEnum.KITCHEN, OrderTypeEnum.REPAIR);

    private final RecycleOrderService recycleOrderService;
    private final KitchenReportService kitchenReportService;
    private final RepairOrderService repairOrderService;

    /** 用户提交评价：订单已完成 + 归属本人 + 未评价过（uk），返回落库记录 */
    @Transactional
    public OrderReview submit(Long userId, ReviewCreateDTO dto) {
        OrderTypeEnum orderType = OrderTypeEnum.of(dto.getOrderType());
        if (orderType == null || !REVIEWABLE.contains(orderType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 RECYCLE/KITCHEN/REPAIR 订单评价");
        }
        boolean already = lambdaQuery()
                .eq(OrderReview::getOrderType, orderType.getCode())
                .eq(OrderReview::getOrderId, dto.getOrderId())
                .eq(OrderReview::getUserId, userId)
                .exists();
        if (already) {
            throw new BusinessException(ResultCode.FAILED, "该订单已评价");
        }
        OrderReview review = new OrderReview();
        review.setOrderType(orderType.getCode());
        review.setOrderId(dto.getOrderId());
        review.setUserId(userId);
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());
        review.setStaffId(resolveStaffId(orderType, dto.getOrderId(), userId));
        save(review);
        return review;
    }

    /** 我的评价分页（连业务订单号展示） */
    public PageVO<ReviewVO> pageMine(Long userId, int pageNum, int pageSize) {
        Page<OrderReview> p = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrderReview>()
                        .eq(OrderReview::getUserId, userId)
                        .orderByDesc(OrderReview::getId));
        Map<String, Map<Long, String>> noByType = orderNosOf(p.getRecords());
        List<ReviewVO> rows = new ArrayList<>(p.getRecords().size());
        for (OrderReview r : p.getRecords()) {
            ReviewVO vo = new ReviewVO();
            vo.setId(r.getId());
            vo.setOrderType(r.getOrderType());
            vo.setOrderId(r.getOrderId());
            vo.setOrderNo(noByType.getOrDefault(r.getOrderType(), Map.of())
                    .getOrDefault(r.getOrderId(), String.valueOf(r.getOrderId())));
            vo.setRating(r.getRating());
            vo.setComment(r.getComment());
            vo.setCreateTime(r.getCreateTime());
            rows.add(vo);
        }
        return PageVO.of(p.getTotal(), p.getPages(), rows);
    }

    /** 本单是否已评价（订单详情页回显星级） */
    public ReviewCheckVO check(Long userId, String orderType, Long orderId) {
        OrderTypeEnum type = OrderTypeEnum.of(orderType);
        if (type == null || !REVIEWABLE.contains(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "orderType 不合法");
        }
        OrderReview review = getOne(new LambdaQueryWrapper<OrderReview>()
                .eq(OrderReview::getOrderType, type.getCode())
                .eq(OrderReview::getOrderId, orderId)
                .eq(OrderReview::getUserId, userId));
        ReviewCheckVO vo = new ReviewCheckVO();
        vo.setReviewed(review != null);
        if (review != null) {
            vo.setRating(review.getRating());
        }
        return vo;
    }

    /**
     * 按订单类型取服务员工，同时完成归属与状态校验：
     * RECYCLE 3 已完成 / REPAIR 3 已完成（RepairStatusEnum.COMPLETED）/ KITCHEN 非风控冻结（无员工，返回 null）
     */
    private Long resolveStaffId(OrderTypeEnum type, Long orderId, Long userId) {
        return switch (type) {
            case RECYCLE -> {
                RecycleOrder order = recycleOrderService.requireOrder(orderId);
                requireOwner(order.getUserId(), userId);
                if (!OrderStatusEnum.COMPLETED.getCode().equals(order.getStatus())) {
                    throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "仅已完成订单可评价");
                }
                yield order.getStaffId();
            }
            case REPAIR -> {
                RepairOrder order = repairOrderService.requireOrder(orderId);
                requireOwner(order.getUserId(), userId);
                if (!RepairStatusEnum.COMPLETED.getCode().equals(order.getStatus())) {
                    throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "仅已完成订单可评价");
                }
                yield order.getStaffId();
            }
            case KITCHEN -> {
                KitchenReport report = kitchenReportService.getById(orderId);
                if (report == null) {
                    throw new BusinessException("垃圾投放记录不存在");
                }
                requireOwner(report.getUserId(), userId);
                if (report.getStatus() == null
                        || report.getStatus() != KitchenReport.STATUS_APPROVED) {
                    throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "投放记录审核通过后才可评价");
                }
                yield null;
            }
            default -> null;
        };
    }

    private static void requireOwner(Long orderUserId, Long userId) {
        if (orderUserId != null && !orderUserId.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 评价列表 → 各订单类型的 orderId → orderNo 映射（垃圾投放无单号，展示 KR+id） */
    private Map<String, Map<Long, String>> orderNosOf(List<OrderReview> reviews) {
        Map<String, Map<Long, String>> result = new HashMap<>();
        for (OrderTypeEnum type : REVIEWABLE) {
            List<Long> ids = reviews.stream()
                    .filter(r -> type.getCode().equals(r.getOrderType()))
                    .map(OrderReview::getOrderId)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                continue;
            }
            Map<Long, String> nos = switch (type) {
                case RECYCLE -> recycleOrderService.listByIds(ids).stream()
                        .collect(Collectors.toMap(RecycleOrder::getId, RecycleOrder::getOrderNo));
                case REPAIR -> repairOrderService.listByIds(ids).stream()
                        .collect(Collectors.toMap(RepairOrder::getId, RepairOrder::getOrderNo));
                case KITCHEN -> kitchenReportService.listByIds(ids).stream()
                        .collect(Collectors.toMap(KitchenReport::getId, r -> "KR" + r.getId()));
                default -> Map.of();
            };
            result.put(type.getCode(), nos);
        }
        return result;
    }
}

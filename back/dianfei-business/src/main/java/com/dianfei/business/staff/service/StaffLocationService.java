package com.dianfei.business.staff.service;

import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.vo.StaffLocationVO;
import com.dianfei.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 员工实时位置（GapFill B2，契约 C3）：员工接单后每 30s 上报 → Redis `staff:loc:{userId}` 存 "lng,lat" TTL 2h；
 * 用户端按回收订单查接单员工位置，无接单员工/无上报返回 null（前端降级文案）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffLocationService {

    private static final String KEY_PREFIX = "staff:loc:";
    private static final Duration TTL = Duration.ofHours(2);

    private final RedisUtils redisUtils;
    private final RecycleOrderService recycleOrderService;
    private final StaffService staffService;

    /** 员工上报当前位置（覆盖写 + 刷新 TTL） */
    public void report(Long userId, BigDecimal lng, BigDecimal lat) {
        BizStaff staff = staffService.requireOnDutyByUserId(userId);
        if (staff.getStaffType() != com.dianfei.common.enums.StaffTypeEnum.RECYCLER.getCode()
                && staff.getStaffType() != com.dianfei.common.enums.StaffTypeEnum.TECHNICIAN.getCode()) {
            throw new com.dianfei.common.exception.BusinessException("当前员工类型不支持位置上报");
        }
        String value = lng.stripTrailingZeros().toPlainString()
                + "," + lat.stripTrailingZeros().toPlainString();
        redisUtils.set(KEY_PREFIX + userId, value, TTL);
    }

    /** 回收订单接单员工实时位置；订单无接单员工或无位置上报返回 null */
    public StaffLocationVO getByRecycleOrder(Long orderId, Long userId) {
        RecycleOrder order = recycleOrderService.requireOrder(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new com.dianfei.common.exception.BusinessException(
                    com.dianfei.common.result.ResultCode.FORBIDDEN, "无权查看该订单位置");
        }
        if (order.getStaffId() == null) {
            return null;
        }
        BizStaff staff = staffService.getById(order.getStaffId());
        if (staff == null || staff.getUserId() == null) {
            return null;
        }
        String raw = redisUtils.get(KEY_PREFIX + staff.getUserId());
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2) {
            return null;
        }
        StaffLocationVO vo = new StaffLocationVO();
        vo.setLng(new BigDecimal(parts[0]));
        vo.setLat(new BigDecimal(parts[1]));
        vo.setStaffName(staff == null ? "回收员" : staff.getStaffName());
        vo.setStaffUserId(staff == null ? null : staff.getUserId());
        return vo;
    }
}

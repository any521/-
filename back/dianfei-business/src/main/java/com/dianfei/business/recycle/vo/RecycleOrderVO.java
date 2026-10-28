package com.dianfei.business.recycle.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收订单出参 VO（B3）：Long 统一转 String，含状态描述与实收明细
 */
@Data
public class RecycleOrderVO {

    private String id;
    private String orderNo;
    private String userId;
    private String staffId;
    /** 接单/指派回收员姓名（详情用） */
    private String staffName;
    /** 接单回收员电话（biz_staff.phone，未接单为空） */
    private String staffPhone;
    /** 下单客户姓名（sys_user.nickname/username） */
    private String customerName;
    /** 下单客户电话（sys_user.phone，未填写为空） */
    private String customerPhone;
    private String areaId;
    /** 所属片区名（biz_area.area_name，员工端全量列表/详情展示用） */
    private String areaName;
    private String category;
    private BigDecimal estWeight;
    private BigDecimal weight;
    private Integer quantity;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private LocalDateTime appointTime;
    private Integer status;
    private String statusDesc;
    private BigDecimal amount;
    private String transferTo;
    /** 联动来源维修订单 ID（11.2 溯源） */
    private String repairRef;
    private String cancelReason;
    private String exceptionReason;
    /** 用户承诺服务费积分（onsite02，0=未使用） */
    private Long servicePoints;
    /** 服务费结算状态（onsite02：0未结算 1已结算 2异常转人工） */
    private Integer settleStatus;
    /** 服务费积分（onsite02：同 servicePoints，结算卡展示用） */
    private Long settlePoints;
    /** 服务费结算结果文案（onsite02：无服务费为 null） */
    private String settleResult;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    /** 实收明细（完成登记后） */
    private List<ItemVO> items;

    @Data
    public static class ItemVO {
        private String category;
        private BigDecimal weight;
        private BigDecimal amount;
    }
}

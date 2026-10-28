package com.dianfei.business.repair.vo;

import com.dianfei.business.repair.diagnose.RepairDiagnoseContract;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 维修工单出参（C1）。
 */
@Data
public class RepairOrderVO {

    private String id;
    private String orderNo;
    private String userId;
    private String staffId;
    /** 接单/指派技术员姓名 */
    private String staffName;
    /** 接单技术员电话（biz_staff.phone，未接单为空） */
    private String staffPhone;
    /** 下单客户姓名（sys_user.nickname/username） */
    private String customerName;
    /** 下单客户电话（sys_user.phone，未填写为空） */
    private String customerPhone;
    private String areaId;
    /** 所属片区名（biz_area.area_name，员工端全量列表/详情展示用） */
    private String areaName;
    private String faultType;
    private String faultDesc;
    private String address;
    private LocalDateTime appointStart;
    private LocalDateTime appointEnd;
    private Integer oldForNew;
    private Integer gasDelivery;
    /** F2 预诊结果（解析自 diagnoseJson，AI 降级时为空） */
    private RepairDiagnoseContract.Response diagnose;
    private Integer repairable;
    private String scienceNote;
    /** 联动生成的回收订单 ID */
    private String recycleRef;
    private Integer status;
    private String statusDesc;
    private String cancelReason;
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
    /** 配件明细 */
    private List<PartVO> parts;

    @Data
    public static class PartVO {
        private String partName;
        private String partModel;
        private Integer quantity;
        private BigDecimal price;
    }
}

package com.dianfei.business.kitchen.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 垃圾投放上报（B4，biz_kitchen_report）：
 * 用户投放上报袋数+投放点+照片，距垃圾回收站 50m 内才允许；一律转工作人员审核，
 * 审核通过即发分（账本 bizKey 幂等），环卫归档时对已通过单幂等兜底。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_kitchen_report")
public class KitchenReport extends BaseEntity {

    /** 待审核（新上报默认，审核通过才发分） */
    public static final int STATUS_PENDING = 0;
    /** 已通过（审核通过即发分） */
    public static final int STATUS_APPROVED = 1;
    /** 已复核（历史风控复核单保留） */
    public static final int STATUS_REVIEWED = 2;
    /** 已驳回（审核不通过不发分） */
    public static final int STATUS_REJECTED = 3;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 所属片区（聚合与风控户均基数） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    /** 投放点 */
    private String pointName;

    private Integer bagCount;

    /** 投放照片（MinIO） */
    private String photoUrl;

    /** 投放经度（50m 站点校验） */
    private BigDecimal lng;

    /** 投放纬度 */
    private BigDecimal lat;

    private LocalDateTime reportTime;

    /** 0待审核 1已通过 2已复核 3已驳回 */
    private Integer status;

    /** 审核驳回原因 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;
}

package com.dianfei.business.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工请假申请（biz_leave_request，C4）：员工端提交，后台审核；
 * 审核通过后请假区间内已有排班自动移除
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_leave_request")
public class LeaveRequest extends BaseEntity {

    /** 待审核 */
    public static final int STATUS_PENDING = 0;
    /** 已通过 */
    public static final int STATUS_APPROVED = 1;
    /** 已驳回 */
    public static final int STATUS_REJECTED = 2;

    private Long staffId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    /** 0待审核 1已通过 2已驳回 */
    private Integer status;

    /** 审核人 */
    private Long auditBy;

    private String auditRemark;

    private LocalDateTime auditTime;
}

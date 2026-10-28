package com.dianfei.business.staff.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工（B1）：三类员工（1回收员 2环卫员 3技术员），绑定片区，需审核上岗
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_staff")
public class BizStaff extends BaseEntity {

    /** 审核状态：待审核 */
    public static final int AUDIT_PENDING = 0;
    /** 审核状态：已通过（可上岗） */
    public static final int AUDIT_APPROVED = 1;
    /** 审核状态：已驳回 */
    public static final int AUDIT_REJECTED = 2;

    /** 关联 sys_user */
    private Long userId;

    private String staffName;

    private String phone;

    /** 1回收员 2环卫员 3技术员（StaffTypeEnum） */
    private Integer staffType;

    /** 绑定片区 */
    private Long areaId;

    /** 0待审核 1已通过 2已驳回 */
    private Integer auditStatus;

    /** 0禁用 1正常 */
    private Integer status;

    /** 来源：0管理端创建 1用户自助申请（V21，G1） */
    private Integer applyScene;

    /** 申请备注（用户自助申请时填写，重新申请会覆盖） */
    private String applyReason;

    /** 审核备注/驳回原因（管理员审核时填写，V58） */
    private String auditRemark;
}

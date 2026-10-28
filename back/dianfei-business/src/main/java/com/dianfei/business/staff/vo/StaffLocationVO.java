package com.dianfei.business.staff.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 回收订单接单员工实时位置（GET /api/recycle/orders/{id}/staff-location；无接单/无上报 data=null）
 */
@Data
public class StaffLocationVO {

    private BigDecimal lng;

    private BigDecimal lat;

    /** 接单员工姓名（前端文案回显） */
    private String staffName;

    /** 员工用户 ID（冗余展示用） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffUserId;
}

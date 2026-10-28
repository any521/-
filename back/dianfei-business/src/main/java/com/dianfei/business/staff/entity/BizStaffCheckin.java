package com.dianfei.business.staff.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工签到（B04，biz_staff_checkin）：
 * 每人每日一条（uk_checkin_staff_date），on/off 两段打卡，定位仅存描述文本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_staff_checkin")
public class BizStaffCheckin extends BaseEntity {

    /** 关联 biz_staff */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    /** 打卡日期 */
    private LocalDate checkDate;

    /** 上班打卡时间 */
    private LocalDateTime onTime;

    /** 下班打卡时间 */
    private LocalDateTime offTime;

    /** 上班打卡定位描述 */
    private String onLocation;

    /** 下班打卡定位描述 */
    private String offLocation;
}

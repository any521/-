package com.dianfei.business.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工排班（biz_schedule，C4）：节假日排班与跨片区人力调配，
 * 员工+日期+班次防冲突由 ScheduleService 业务校验（兼容逻辑删除）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_schedule")
public class Schedule extends BaseEntity {

    /** 白班 */
    public static final int SHIFT_DAY = 1;
    /** 晚班 */
    public static final int SHIFT_NIGHT = 2;

    /** 正常排班 */
    public static final int BIZ_NORMAL = 0;
    /** 节假日排班 */
    public static final int BIZ_HOLIDAY = 1;
    /** 跨片区支援 */
    public static final int BIZ_SUPPORT = 2;

    private Long staffId;

    /** 排班片区 */
    private Long areaId;

    private LocalDate workDate;

    /** 1白班 2晚班 */
    private Integer shiftType;

    /** 0正常 1节假日 2跨片区支援 */
    private Integer bizType;

    /** 支援目标片区（bizType=2 必填） */
    private Long supportAreaId;

    private String remark;
}

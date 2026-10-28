package com.dianfei.system.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工作人员身份摘要（ProfileVO.staffInfo，G1 双身份契约 C-O1）：
 * biz_staff 最新一条（deleted=0 不限 auditStatus）；未申请为 null
 */
@Data
@Schema(description = "工作人员身份摘要")
public class StaffInfoVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    /** 1回收员 2环卫员 3技术员 */
    private Integer staffType;

    private String staffName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    /** 0待审核 1已通过 2已驳回 */
    private Integer auditStatus;

    /** 0禁用 1正常（在岗判定：auditStatus=1 且 status=1） */
    private Integer status;

    /** 入职日期（biz_staff.create_time 建档案日期，yyyy-MM-dd；未建档为 null） */
    @Schema(description = "入职日期（建档案日期 yyyy-MM-dd）")
    private String hireDate;
}

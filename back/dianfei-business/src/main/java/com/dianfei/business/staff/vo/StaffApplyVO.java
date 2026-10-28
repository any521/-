package com.dianfei.business.staff.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 工作人员申请状态（GET /api/staff/apply/status，G1）：mine.vue 三态卡片数据源
 */
@Data
@Schema(description = "工作人员申请状态")
public class StaffApplyVO {

    /** 是否已提交过申请（无记录 false） */
    private Boolean applied;

    /** 0待审核 1已通过 2已驳回（未申请为 null） */
    private Integer auditStatus;

    /** 1回收员 2清运工 3维修技术员（未申请为 null） */
    private Integer staffType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffId;

    /** 驳回原因（已驳回时展示；旧数据无审核备注时退回申请理由） */
    private String reason;
}

package com.dianfei.business.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 排班保存入参（C4）：片区取员工绑定片区；跨片区支援（bizType=2）时 supportAreaId 必填
 */
@Data
public class ScheduleSaveDTO {

    @NotNull(message = "员工不能为空")
    private Long staffId;

    @NotNull(message = "排班日期不能为空")
    private LocalDate workDate;

    @Schema(description = "1白班 2晚班")
    @NotNull(message = "班次不能为空")
    private Integer shiftType;

    @Schema(description = "0正常 1节假日 2跨片区支援")
    @NotNull(message = "排班类型不能为空")
    private Integer bizType;

    @Schema(description = "支援目标片区（bizType=2 必填）")
    private Long supportAreaId;

    private String remark;
}

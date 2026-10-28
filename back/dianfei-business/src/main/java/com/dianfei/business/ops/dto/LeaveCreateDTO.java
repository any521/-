package com.dianfei.business.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工请假申请入参（C4）
 */
@Data
public class LeaveCreateDTO {

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotBlank(message = "请假事由不能为空")
    @Size(max = 255, message = "请假事由不超过 255 字")
    private String reason;
}

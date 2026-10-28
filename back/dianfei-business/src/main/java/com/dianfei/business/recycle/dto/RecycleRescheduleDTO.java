package com.dianfei.business.recycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户端回收单改期入参（G8）：{date, timeSlot}，与前端 rescheduleOrder body 完全对齐
 */
@Data
public class RecycleRescheduleDTO {

    @NotNull(message = "改期日期不能为空")
    private LocalDate date;

    /** 时段 '09:00-11:00'，改期到当天该时段起始时刻 */
    @NotBlank(message = "改期时段不能为空")
    private String timeSlot;
}

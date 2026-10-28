package com.dianfei.business.staff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 员工自助换片区入参（PUT /api/staff/me/area） */
@Data
public class AreaChangeDTO {

    /** 目标片区 ID（雪花 Long） */
    @NotNull
    private Long areaId;
}

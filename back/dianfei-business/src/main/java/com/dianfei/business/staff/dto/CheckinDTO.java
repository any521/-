package com.dianfei.business.staff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 员工签到入参（POST /api/staff/checkin）
 */
@Data
public class CheckinDTO {

    /** 'ON' 上班打卡 / 'OFF' 下班打卡 */
    @NotBlank(message = "打卡类型不能为空")
    private String type;

    /** 打卡定位描述 */
    private String location;
}

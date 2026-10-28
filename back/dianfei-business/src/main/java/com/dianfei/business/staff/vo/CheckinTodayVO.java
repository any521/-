package com.dianfei.business.staff.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 今日签到状态（GET /api/staff/checkin/today）：未打为 ""，勿返 null
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckinTodayVO {

    private String onTime;

    private String offTime;
}

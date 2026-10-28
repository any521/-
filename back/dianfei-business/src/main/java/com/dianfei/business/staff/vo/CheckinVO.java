package com.dianfei.business.staff.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工签到出参（POST /api/staff/checkin）：本次打卡时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckinVO {

    /** 本次打卡时间 yyyy-MM-dd HH:mm:ss */
    private String time;
}

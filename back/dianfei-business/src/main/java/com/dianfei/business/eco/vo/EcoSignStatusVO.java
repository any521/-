package com.dianfei.business.eco.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 签到状态（GET /api/eco/sign-in/status）：本月签到日历 + 连续天数 + 累计
 */
@Data
@Builder
public class EcoSignStatusVO {

    /** 本月每日（yyyy-MM-dd），signed 是否已签 */
    private List<MonthDay> monthDays;

    /** 连续签到天数（截至今天；今天未签则从昨天回溯） */
    private int streakDays;

    /** 历史累计签到次数 */
    private int totalSigns;

    /** 单次签到积分（sys_config eco.sign-in-points 热更） */
    private int signPoints;

    @Data
    @Builder
    public static class MonthDay {
        private String date;
        private boolean signed;
    }
}

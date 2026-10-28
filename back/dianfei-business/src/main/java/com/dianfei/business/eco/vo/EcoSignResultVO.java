package com.dianfei.business.eco.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 签到结果（POST /api/eco/sign-in）
 */
@Data
@Builder
public class EcoSignResultVO {

    /** 本次签到发放积分 */
    private long points;

    /** 签到后连续天数（含今日） */
    private int streakDays;
}

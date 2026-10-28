package com.dianfei.business.eco.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 成就卡片（GET /api/eco/achievements）：全量成就 + 用户达成/领取态
 */
@Data
@Builder
public class EcoAchievementVO {

    private String code;

    private String name;

    private String description;

    /** 达成奖励积分 */
    private int points;

    /** 是否已达成（业务数据判定；曾解锁过的保持 true） */
    private boolean unlocked;

    /** 是否已领取（claimed=1） */
    private boolean claimed;
}

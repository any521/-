package com.dianfei.business.eco.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 成就领取结果（POST /api/eco/achievements/{code}/claim，幂等）
 */
@Data
@Builder
public class EcoClaimVO {

    private String code;

    /** 本次领取奖励积分（重复领取幂等返回同值） */
    private int points;
}

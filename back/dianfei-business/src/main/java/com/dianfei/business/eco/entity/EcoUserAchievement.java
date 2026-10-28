package com.dianfei.business.eco.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户环保成就（LoopFill B5 创意 C1，eco_user_achievement）：
 * uk(user_id, achievement_id) 防重复；claimed 0未领取 1已领取。
 * 达成自动解锁（写行 claimed=0）→ 用户领取（claimed=1 + 账本入账）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eco_user_achievement")
public class EcoUserAchievement extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long achievementId;

    /** 0未领取 1已领取 */
    private Integer claimed;
}

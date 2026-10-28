package com.dianfei.business.eco.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 环保成就定义（LoopFill B5 创意 C1，eco_achievement）：
 * 种子 4 类（RECYCLE_FIRST/KITCHEN_FIRST/RECYCLE_TEN/SIGN_SEVEN），id 固定小值
 * （claim 幂等键 ECO:{achievementId}:GAIN 依赖其稳定），V26 迁移幂等写入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eco_achievement")
public class EcoAchievement extends BaseEntity {

    private String code;

    private String name;

    private String description;

    /** 达成奖励积分 */
    private Integer points;

    private Integer sort;
}

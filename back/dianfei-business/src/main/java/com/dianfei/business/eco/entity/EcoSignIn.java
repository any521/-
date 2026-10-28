package com.dianfei.business.eco.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 环保每日签到（LoopFill B5 创意 C1，eco_sign_in）：
 * 每日一次 +5 积分（sys_config eco.sign-in-points 热更），uk(user_id, sign_date) 防重复，
 * 积分入账走账本唯一入口 CarbonLedgerOps.grantEco（rule_code=ECO_SIGN）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eco_sign_in")
public class EcoSignIn extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private LocalDate signDate;

    /** 本次签到发放积分 */
    private Integer points;
}

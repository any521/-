package com.dianfei.business.diy.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 片区环保榜行（LoopFill B6）：回收 kg / 垃圾投放袋数 / 碳减排 kg 三指标 + 加权总分
 * （totalScore = recycleKg + kitchenBags × 2 + carbonKg，榜单排序依据）
 */
@Data
@Builder
public class DiyRankVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long areaId;

    private String areaName;

    /** 回收实收重量 kg（仅已完成单） */
    private BigDecimal recycleKg;

    /** 垃圾投放袋数（全部上报，含冻结复核单） */
    private Long kitchenBags;

    /** 碳减排 kg（碳流水 JOIN 订单取片区，维修/兑换无片区归属不纳入） */
    private BigDecimal carbonKg;

    /** 加权总分（1 位小数） */
    private BigDecimal totalScore;
}

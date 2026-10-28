package com.dianfei.business.staff.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 路线停靠点（orderId 为雪花 ID 字符串，防前端精度丢失）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStopVO {

    private String orderId;

    /** 1 起连续编号 */
    private Integer seq;

    private String address;

    /** 无真实姓名数据，固定「预约客户」（前端已适配） */
    private String customerName;

    /** 预约时间 yyyy-MM-dd HH:mm:ss（厨余为清运日期） */
    private String appointTime;

    /** 距上一停靠点里程 km（首点 0） */
    private BigDecimal distanceKm;

    /** 已完成：回收 status>=3、厨余 status=1、维修 status>=3 */
    private Boolean done;
}

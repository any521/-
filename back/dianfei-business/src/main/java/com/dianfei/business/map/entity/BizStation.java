package com.dianfei.business.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 回收站点（onsite04 V24）：地图导航目标，类型 RECYCLE 回收站 / KITCHEN 厨余站 / COLLECT 集散点
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_station")
public class BizStation extends BaseEntity {

    private String name;

    /** RECYCLE / KITCHEN / COLLECT */
    private String stationType;

    private BigDecimal lng;

    private BigDecimal lat;

    /** 地址（供地理编码校准） */
    private String address;

    /** 营业时间文案 */
    private String openHours;

    /** 1开放 0停用 */
    private Integer status;
}

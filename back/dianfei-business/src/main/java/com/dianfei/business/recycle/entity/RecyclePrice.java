package com.dianfei.business.recycle.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 可回收物报价（B3）：category 唯一，后台 upsert 配置（零代码新增品类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recycle_price")
public class RecyclePrice extends BaseEntity {

    /** 品类（字典驱动），唯一 */
    private String category;

    /** 每公斤回收报价（元） */
    private BigDecimal pricePerKg;

    /** 0下架 1生效 */
    private Integer status;

    /** 联网行情地区（查询该品类时所在城市；单品类查询失败不落库，保留上次成功值） */
    private String city;

    /** 较上一交易日价格（元/kg，非表字段）：每日联网同步前价快照（Redis），供涨跌 % 展示 */
    @TableField(exist = false)
    private BigDecimal prevPricePerKg;
}

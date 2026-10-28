package com.dianfei.business.staff.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 片区（B1）：员工服务与数据权限的地理边界，绑定清运阈值与户数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_area")
public class BizArea extends BaseEntity {

    private String areaName;

    private String address;

    /** 片区中心经度（驾驶舱地图标注） */
    private java.math.BigDecimal lng;

    /** 片区中心纬度 */
    private java.math.BigDecimal lat;

    /** 厨余清运阈值（袋），达到即生成次日清运任务 */
    private Integer cleanThreshold;

    /** 片区户数，风控户均基数 */
    private Integer householdCount;

    /** 0停用 1启用 */
    private Integer status;
}

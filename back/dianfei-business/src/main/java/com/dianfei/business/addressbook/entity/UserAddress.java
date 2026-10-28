package com.dianfei.business.addressbook.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 用户地址簿（biz_user_address）：
 * 预约回收/维修/商城收货/跳蚤收货的统一地址来源，省市区 + 详细地址 + 定位经纬度，
 * 可选关联片区 area_id（派单归属）；is_default 同用户至多一个。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_user_address")
public class UserAddress extends BaseEntity {

    private Long userId;

    private String contactName;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String detail;

    private BigDecimal lng;

    private BigDecimal lat;

    /** 关联片区（biz_area，派单归属，可空） */
    private Long areaId;

    /** 标签：家/公司/学校 */
    private String tag;

    /** 0非默认 1默认 */
    private Integer isDefault;
}

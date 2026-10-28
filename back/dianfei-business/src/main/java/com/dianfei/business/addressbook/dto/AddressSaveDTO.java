package com.dianfei.business.addressbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 地址簿新增/更新入参（user_id 从登录上下文取，不信任前端）
 */
@Data
public class AddressSaveDTO {

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 128, message = "详细地址不能超过 128 字")
    private String detail;

    @Size(max = 32, message = "联系人不能超过 32 字")
    private String contactName;

    @Size(max = 20, message = "联系电话不能超过 20 字")
    private String contactPhone;

    @Size(max = 32)
    private String province;

    @Size(max = 32)
    private String city;

    @Size(max = 32)
    private String district;

    private BigDecimal lng;

    private BigDecimal lat;

    /** 关联片区（biz_area，派单归属，可空） */
    private Long areaId;

    @Size(max = 16)
    private String tag;

    /** 1=设为默认（其余同用户地址自动取消默认） */
    private Integer isDefault;
}

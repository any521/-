package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据（业务品类由此驱动，D5）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    /** 所属字典类型编码 */
    private String dictType;

    /** 显示名，如 金属 */
    private String dictLabel;

    /** 值，如 METAL */
    private String dictValue;

    private Integer sort;

    private String remark;

    /** 0 停用 / 1 启用 */
    private Integer status;
}

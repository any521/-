package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参数配置（热更新：改库即刷 Redis 缓存）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    /** 参数键，如 kitchen.report.start-hour */
    private String configKey;

    private String configValue;

    private String configName;

    private String remark;
}

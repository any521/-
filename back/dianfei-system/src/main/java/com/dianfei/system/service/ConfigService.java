package com.dianfei.system.service;

import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.ConfigDTO;
import com.dianfei.system.entity.SysConfig;

/**
 * 参数配置服务（D5 业务规则配置化；热更新：改库即刷 Redis 缓存，业务读 getValue 不重启生效）
 */
public interface ConfigService {

    PageVO<SysConfig> page(PageDTO page, String keyword);

    void add(ConfigDTO dto);

    void edit(ConfigDTO dto);

    void remove(Long id);

    /** 按键读参数值（优先 Redis 缓存，供业务模块热读取） */
    String getValue(String configKey);

    /** 按键读参数值，缺省时返回 defaultValue */
    String getValue(String configKey, String defaultValue);
}

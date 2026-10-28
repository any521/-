package com.dianfei.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.utils.RedisUtils;
import com.dianfei.common.utils.TombstoneUtil;
import com.dianfei.system.dto.ConfigDTO;
import com.dianfei.system.entity.SysConfig;
import com.dianfei.system.mapper.SysConfigMapper;
import com.dianfei.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 参数配置实现：写路径「先库后缓存」保证热更新，读路径「缓存→DB 回填」
 */
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private static final String CONFIG_KEY = "dianfei:config:%s";
    /** 兜底 TTL：写路径会主动刷缓存，TTL 仅防脏数据长期滞留 */
    private static final Duration CONFIG_TTL = Duration.ofHours(24);

    private final SysConfigMapper sysConfigMapper;
    private final RedisUtils redisUtils;

    @Override
    public PageVO<SysConfig> page(PageDTO page, String keyword) {
        Page<SysConfig> result = sysConfigMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<SysConfig>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(SysConfig::getConfigName, keyword)
                                .or().like(SysConfig::getConfigKey, keyword))
                        .orderByAsc(SysConfig::getId));
        return PageVO.of(result);
    }

    @Override
    public void add(ConfigDTO dto) {
        checkKeyUnique(dto.getConfigKey(), null);
        SysConfig config = BeanUtil.copyProperties(dto, SysConfig.class);
        config.setId(null);
        sysConfigMapper.insert(config);
        refreshCache(config.getConfigKey(), config.getConfigValue());
    }

    @Override
    public void edit(ConfigDTO dto) {
        SysConfig old = dto.getId() == null ? null : sysConfigMapper.selectById(dto.getId());
        if (old == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "参数不存在");
        }
        checkKeyUnique(dto.getConfigKey(), dto.getId());
        sysConfigMapper.updateById(BeanUtil.copyProperties(dto, SysConfig.class));
        // 键变更时清掉旧键缓存
        if (!old.getConfigKey().equals(dto.getConfigKey())) {
            redisUtils.delete(CONFIG_KEY.formatted(old.getConfigKey()));
        }
        refreshCache(dto.getConfigKey(), dto.getConfigValue());
    }

    @Override
    public void remove(Long id) {
        SysConfig config = sysConfigMapper.selectById(id);
        if (config == null) {
            return;
        }
        String configKey = config.getConfigKey();
        // 墓碑改写唯一键后再逻辑删除，释放 uk_config_key 供同键重建（列宽 64）
        config.setConfigKey(TombstoneUtil.mark(configKey, config.getId(), 64));
        sysConfigMapper.updateById(config);
        sysConfigMapper.deleteById(id);
        redisUtils.delete(CONFIG_KEY.formatted(configKey));
    }

    @Override
    public String getValue(String configKey) {
        String cached = redisUtils.get(CONFIG_KEY.formatted(configKey));
        if (cached != null) {
            return cached;
        }
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (config == null) {
            return null;
        }
        refreshCache(configKey, config.getConfigValue());
        return config.getConfigValue();
    }

    @Override
    public String getValue(String configKey, String defaultValue) {
        String value = getValue(configKey);
        return value == null ? defaultValue : value;
    }

    private void refreshCache(String configKey, String configValue) {
        redisUtils.set(CONFIG_KEY.formatted(configKey), configValue, CONFIG_TTL);
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        Long count = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .ne(excludeId != null, SysConfig::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "参数键已存在");
        }
    }
}

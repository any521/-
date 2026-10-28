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
import com.dianfei.system.dto.DictDataDTO;
import com.dianfei.system.dto.DictTypeDTO;
import com.dianfei.system.entity.SysDictData;
import com.dianfei.system.entity.SysDictType;
import com.dianfei.system.mapper.SysDictDataMapper;
import com.dianfei.system.mapper.SysDictTypeMapper;
import com.dianfei.system.service.DictService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 字典服务实现：读走 Redis 缓存（dianfei:dict:{type}），任何改动即删缓存下次读时重建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final String DICT_KEY = "dianfei:dict:%s";
    /** 缓存兜底 TTL（改动会主动失效，TTL 仅防脏数据长期滞留） */
    private static final Duration DICT_TTL = Duration.ofHours(24);

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictDataMapper sysDictDataMapper;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    // ---------- 类型 ----------

    @Override
    public PageVO<SysDictType> pageType(PageDTO page, String keyword) {
        Page<SysDictType> result = sysDictTypeMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<SysDictType>()
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(SysDictType::getDictName, keyword)
                                .or().like(SysDictType::getDictType, keyword))
                        .orderByAsc(SysDictType::getId));
        return PageVO.of(result);
    }

    @Override
    public void addType(DictTypeDTO dto) {
        checkTypeUnique(dto.getDictType(), null);
        SysDictType type = BeanUtil.copyProperties(dto, SysDictType.class);
        type.setId(null);
        sysDictTypeMapper.insert(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editType(DictTypeDTO dto) {
        SysDictType old = dto.getId() == null ? null : sysDictTypeMapper.selectById(dto.getId());
        if (old == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字典类型不存在");
        }
        checkTypeUnique(dto.getDictType(), dto.getId());
        // 类型编码变更时同步字典数据的 dict_type
        if (!old.getDictType().equals(dto.getDictType())) {
            SysDictData patch = new SysDictData();
            patch.setDictType(dto.getDictType());
            sysDictDataMapper.update(patch, new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, old.getDictType()));
            evictCache(old.getDictType());
        }
        sysDictTypeMapper.updateById(BeanUtil.copyProperties(dto, SysDictType.class));
        evictCache(dto.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeType(Long id) {
        SysDictType type = sysDictTypeMapper.selectById(id);
        if (type == null) {
            return;
        }
        Long count = sysDictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, type.getDictType()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该类型下存在字典数据，不允许删除");
        }
        String dictType = type.getDictType();
        // 墓碑改写唯一键后再逻辑删除，释放 uk_dict_type 供同码重建（列宽 64）
        type.setDictType(TombstoneUtil.mark(dictType, type.getId(), 64));
        sysDictTypeMapper.updateById(type);
        sysDictTypeMapper.deleteById(id);
        evictCache(dictType);
    }

    // ---------- 数据 ----------

    @Override
    public List<SysDictData> dataByType(String dictType) {
        String cached = redisUtils.get(DICT_KEY.formatted(dictType));
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("[字典缓存] 反序列化失败，回源 DB：{}", dictType, e);
            }
        }
        List<SysDictData> list = sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 1)
                .orderByAsc(SysDictData::getSort));
        try {
            redisUtils.set(DICT_KEY.formatted(dictType),
                    objectMapper.writeValueAsString(list), DICT_TTL);
        } catch (Exception e) {
            log.warn("[字典缓存] 写入失败，不影响主流程：{}", dictType, e);
        }
        return list;
    }

    @Override
    public List<SysDictData> listData(String dictType) {
        return sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(StrUtil.isNotBlank(dictType), SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getDictType)
                .orderByAsc(SysDictData::getSort));
    }

    @Override
    public void addData(DictDataDTO dto) {
        checkTypeExists(dto.getDictType());
        SysDictData data = BeanUtil.copyProperties(dto, SysDictData.class);
        data.setId(null);
        sysDictDataMapper.insert(data);
        evictCache(dto.getDictType());
    }

    @Override
    public void editData(DictDataDTO dto) {
        SysDictData old = dto.getId() == null ? null : sysDictDataMapper.selectById(dto.getId());
        if (old == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字典数据不存在");
        }
        checkTypeExists(dto.getDictType());
        sysDictDataMapper.updateById(BeanUtil.copyProperties(dto, SysDictData.class));
        evictCache(old.getDictType());
        evictCache(dto.getDictType());
    }

    @Override
    public void removeData(Long id) {
        SysDictData data = sysDictDataMapper.selectById(id);
        if (data == null) {
            return;
        }
        sysDictDataMapper.deleteById(id);
        evictCache(data.getDictType());
    }

    private void evictCache(String dictType) {
        redisUtils.delete(DICT_KEY.formatted(dictType));
    }

    private void checkTypeUnique(String dictType, Long excludeId) {
        Long count = sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字典类型编码已存在");
        }
    }

    private void checkTypeExists(String dictType) {
        Long count = sysDictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType));
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字典类型不存在");
        }
    }
}

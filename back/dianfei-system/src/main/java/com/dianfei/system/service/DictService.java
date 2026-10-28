package com.dianfei.system.service;

import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.DictDataDTO;
import com.dianfei.system.dto.DictTypeDTO;
import com.dianfei.system.entity.SysDictData;
import com.dianfei.system.entity.SysDictType;

import java.util.List;

/**
 * 字典服务（业务品类由字典驱动 D5；读走 Redis 缓存，改动即刷）
 */
public interface DictService {

    // ---------- 类型 ----------

    PageVO<SysDictType> pageType(PageDTO page, String keyword);

    void addType(DictTypeDTO dto);

    void editType(DictTypeDTO dto);

    void removeType(Long id);

    // ---------- 数据 ----------

    /** 按类型查启用的字典数据（走 Redis 缓存，用户/员工/管理端通用） */
    List<SysDictData> dataByType(String dictType);

    /** 管理端全量列表（含停用，不走缓存） */
    List<SysDictData> listData(String dictType);

    void addData(DictDataDTO dto);

    void editData(DictDataDTO dto);

    void removeData(Long id);
}

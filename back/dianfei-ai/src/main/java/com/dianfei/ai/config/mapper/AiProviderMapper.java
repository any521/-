package com.dianfei.ai.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.ai.config.entity.AiProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 供应商配置 Mapper
 */
@Mapper
public interface AiProviderMapper extends BaseMapper<AiProvider> {
}

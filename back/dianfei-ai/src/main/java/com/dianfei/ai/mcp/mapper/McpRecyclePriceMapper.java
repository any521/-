package com.dianfei.ai.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.ai.mcp.entity.McpRecyclePrice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可回收物报价只读查询（get_recycling_price 工具）
 */
@Mapper
public interface McpRecyclePriceMapper extends BaseMapper<McpRecyclePrice> {
}

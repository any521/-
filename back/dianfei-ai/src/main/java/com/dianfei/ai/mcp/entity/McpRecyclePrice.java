package com.dianfei.ai.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 可回收物报价（biz_recycle_price，Track B 冻结 DDL 的 MCP 侧只读最小映射）：
 * 仅供 get_recycling_price 工具查询；B 就绪后以 B 的完整实体为准
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recycle_price")
public class McpRecyclePrice extends BaseEntity {

    /** 品类（字典驱动） */
    private String category;

    /** 每公斤回收报价（元） */
    private BigDecimal pricePerKg;

    /** 0下架 1生效 */
    private Integer status;
}

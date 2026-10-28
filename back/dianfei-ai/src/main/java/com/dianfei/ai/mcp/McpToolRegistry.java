package com.dianfei.ai.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F10 MCP 开放工具清单（08 章 8.2 冻结契约）：
 * 工具名 / 作用域（scope 最小授权）/ 是否需用户端二次确认 / 入参说明。
 * tools/list 直接返回本注册表，即对第三方的唯一事实源。
 */
public final class  McpToolRegistry {

    /** 只读作用域 */
    public static final String SCOPE_READ = "read";
    /** 写操作作用域（下单类，需二次确认） */
    public static final String SCOPE_WRITE = "write";

    /** MCP 工具定义（对齐 08 章工具表） */
    public record McpTool(String name, String scope, boolean needConfirm,
                          String description, Map<String, String> params) {
    }

    public static final List<McpTool> TOOLS = List.of(
            new McpTool("query_carbon_account", SCOPE_READ, false,
                    "查询用户碳积分余额、冻结积分与累计碳减排量",
                    Map.of()),
            new McpTool("list_recycling_orders", SCOPE_READ, false,
                    "查询用户历史与进行中的回收工单（最近 N 条）",
                    Map.of("limit", "返回条数，默认 10，最大 20")),
            new McpTool("get_recycling_price", SCOPE_READ, false,
                    "查询当前品类回收报价（biz_recycle_price 生效项）",
                    Map.of("category", "品类，可选；缺省返回全部生效报价")),
            new McpTool("get_carbon_certificate", SCOPE_READ, false,
                    "获取碳流水可信证书：哈希链校验 + Merkle 路径到每日锚定根",
                    Map.of("log_id", "碳流水 ID，必填")),
            new McpTool("book_recycling", SCOPE_WRITE, true,
                    "创建回收预约（需用户端二次确认后生效）",
                    Map.of("category", "品类，必填",
                            "appoint_time", "预约上门时间 yyyy-MM-dd HH:mm，必填",
                            "address", "上门地址，必填",
                            "area_id", "片区 ID，可选（缺省取用户默认片区）")),
            new McpTool("redeem_points", SCOPE_WRITE, true,
                    "积分兑换商城权益（需用户端二次确认后生效）",
                    Map.of("product_id", "商品 ID，必填",
                            "quantity", "数量，默认 1",
                            "address", "收货地址（实物商品必填）")));

    private McpToolRegistry() {
    }

    public static Optional<McpTool> find(String name) {
        return TOOLS.stream().filter(t -> t.name().equals(name)).findFirst();
    }
}

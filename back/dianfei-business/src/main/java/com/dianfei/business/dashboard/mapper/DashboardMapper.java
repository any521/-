package com.dianfei.business.dashboard.mapper;

import com.dianfei.business.dashboard.excel.RecycleLedgerRow;
import com.dianfei.business.dashboard.vo.AreaRankVO;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据大屏聚合 Mapper（C5）：只读聚合 SQL（MVP 单库 + 服务层 1s 缓存，对齐 11.2）；
 * 语法兼容 MySQL 与 H2（EXTRACT 取年月，避免 DATE_FORMAT）
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) AS total_orders, "
            + "COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS finished_orders, "
            + "COALESCE(SUM(CASE WHEN status = 3 THEN weight ELSE 0 END), 0) AS total_weight, "
            + "COALESCE(SUM(CASE WHEN create_time >= #{todayStart} THEN 1 ELSE 0 END), 0) AS today_orders "
            + "FROM biz_recycle_order WHERE deleted = 0")
    RecycleStat selectRecycleStat(@Param("todayStart") LocalDateTime todayStart);

    @Select("SELECT COALESCE(SUM(bag_count), 0) AS total_bags, "
            + "COALESCE(SUM(CASE WHEN report_time >= #{todayStart} THEN bag_count ELSE 0 END), 0) AS today_bags "
            + "FROM biz_kitchen_report WHERE deleted = 0")
    KitchenStat selectKitchenStat(@Param("todayStart") LocalDateTime todayStart);

    @Select("SELECT COUNT(*) AS total_orders, "
            + "COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS finished_orders, "
            + "COALESCE(SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END), 0) AS transferred_orders "
            + "FROM biz_repair_order WHERE deleted = 0")
    RepairStat selectRepairStat();

    /** 累计碳减排：可信账本账户累计口径（D16） */
    @Select("SELECT COALESCE(SUM(total_carbon_reduced), 0) FROM biz_carbon_account WHERE deleted = 0")
    BigDecimal selectCarbonReducedTotal();

    @Select("SELECT a.id AS area_id, a.area_name, a.lng, a.lat, "
            + "COALESCE(r.cnt, 0) AS recycle_orders, COALESCE(r.total_weight, 0) AS recycle_weight, "
            + "COALESCE(k.bags, 0) AS kitchen_bags, COALESCE(c.amount, 0) AS carbon_reduced "
            + "FROM biz_area a "
            + "LEFT JOIN (SELECT area_id, COUNT(*) AS cnt, SUM(weight) AS total_weight "
            + "  FROM biz_recycle_order WHERE deleted = 0 AND status = 3 GROUP BY area_id) r ON r.area_id = a.id "
            + "LEFT JOIN (SELECT area_id, SUM(bag_count) AS bags "
            + "  FROM biz_kitchen_report WHERE deleted = 0 GROUP BY area_id) k ON k.area_id = a.id "
            + "LEFT JOIN (SELECT o.area_id, SUM(l.carbon_reduced) AS amount "
            + "  FROM biz_carbon_log l JOIN biz_recycle_order o ON o.id = l.order_id AND l.order_type = 'RECYCLE' "
            + "  WHERE l.deleted = 0 AND l.type = 1 GROUP BY o.area_id) c ON c.area_id = a.id "
            + "WHERE a.deleted = 0 ORDER BY recycle_weight DESC, kitchen_bags DESC")
    List<AreaRankVO> selectAreaRanking();

    @Select("SELECT EXTRACT(YEAR FROM finish_time) AS y, EXTRACT(MONTH FROM finish_time) AS m, "
            + "COUNT(*) AS cnt, COALESCE(SUM(weight), 0) AS amount "
            + "FROM biz_recycle_order WHERE deleted = 0 AND status = 3 AND finish_time >= #{from} "
            + "GROUP BY EXTRACT(YEAR FROM finish_time), EXTRACT(MONTH FROM finish_time)")
    List<MonthRow> selectMonthlyRecycle(@Param("from") LocalDateTime from);

    @Select("SELECT EXTRACT(YEAR FROM report_time) AS y, EXTRACT(MONTH FROM report_time) AS m, "
            + "COUNT(*) AS cnt, COALESCE(SUM(bag_count), 0) AS amount "
            + "FROM biz_kitchen_report WHERE deleted = 0 AND report_time >= #{from} "
            + "GROUP BY EXTRACT(YEAR FROM report_time), EXTRACT(MONTH FROM report_time)")
    List<MonthRow> selectMonthlyKitchen(@Param("from") LocalDateTime from);

    @Select("SELECT EXTRACT(YEAR FROM create_time) AS y, EXTRACT(MONTH FROM create_time) AS m, "
            + "COUNT(*) AS cnt, 0 AS amount "
            + "FROM biz_repair_order WHERE deleted = 0 AND create_time >= #{from} "
            + "GROUP BY EXTRACT(YEAR FROM create_time), EXTRACT(MONTH FROM create_time)")
    List<MonthRow> selectMonthlyRepair(@Param("from") LocalDateTime from);

    @Select("SELECT EXTRACT(YEAR FROM create_time) AS y, EXTRACT(MONTH FROM create_time) AS m, "
            + "COUNT(*) AS cnt, COALESCE(SUM(carbon_reduced), 0) AS amount "
            + "FROM biz_carbon_log WHERE deleted = 0 AND points > 0 AND create_time >= #{from} "
            + "GROUP BY EXTRACT(YEAR FROM create_time), EXTRACT(MONTH FROM create_time)")
    List<MonthRow> selectMonthlyCarbon(@Param("from") LocalDateTime from);

    /** 回收台账导出行（EasyExcel）：状态在 SQL 内转文案，联表补片区名 */
    @Select("SELECT o.order_no, a.area_name, o.category, o.weight, o.amount, "
            + "CASE o.status WHEN 0 THEN '待接单' WHEN 1 THEN '已接单' WHEN 2 THEN '进行中' "
            + "WHEN 3 THEN '已完成' WHEN 4 THEN '已取消' ELSE '异常' END AS status_text, "
            + "o.finish_time "
            + "FROM biz_recycle_order o LEFT JOIN biz_area a ON a.id = o.area_id "
            + "WHERE o.deleted = 0 ORDER BY o.id DESC")
    List<RecycleLedgerRow> selectRecycleLedger();

    // ==================== 工作台摘要（G6） ====================

    /** 今日单量：回收当日下单未取消 + 垃圾投放当日上报 合计 */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM biz_recycle_order WHERE deleted = 0 AND status <> 4 "
            + "  AND create_time >= #{from} AND create_time < #{to}) "
            + "+ (SELECT COUNT(*) FROM biz_kitchen_report WHERE deleted = 0 "
            + "  AND report_time >= #{from} AND report_time < #{to})")
    long selectTodayOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 今日完成回收重量（status=3 按 finish_time 当日） */
    @Select("SELECT COALESCE(SUM(weight), 0) FROM biz_recycle_order "
            + "WHERE deleted = 0 AND status = 3 AND finish_time >= #{from} AND finish_time < #{to}")
    BigDecimal selectTodayFinishedWeight(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 今日碳减排（账本 type=1 获得流水，create_time 当日） */
    @Select("SELECT COALESCE(SUM(carbon_reduced), 0) FROM biz_carbon_log "
            + "WHERE deleted = 0 AND type = 1 AND create_time >= #{from} AND create_time < #{to}")
    BigDecimal selectTodayCarbon(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** RECYCLE 规则碳系数均值（今日无流水时按回收重量折算） */
    @Select("SELECT AVG(carbon_per_kg) FROM biz_carbon_rule "
            + "WHERE deleted = 0 AND status = 1 AND order_type = 'RECYCLE' AND carbon_per_kg IS NOT NULL")
    BigDecimal selectAvgRecycleCarbonPerKg();

    /** 近 7 日回收单数（按 create_time 当日，排除取消单；7 日窗口内日号唯一） */
    @Select("SELECT EXTRACT(DAY FROM create_time) AS d, COUNT(*) AS cnt FROM biz_recycle_order "
            + "WHERE deleted = 0 AND status <> 4 AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY EXTRACT(DAY FROM create_time)")
    List<DayRow> selectWeekRecycleOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 近 7 日完成重量（按 finish_time 当日，status=3） */
    @Select("SELECT EXTRACT(DAY FROM finish_time) AS d, COALESCE(SUM(weight), 0) AS amount "
            + "FROM biz_recycle_order WHERE deleted = 0 AND status = 3 "
            + "AND finish_time >= #{from} AND finish_time < #{to} GROUP BY EXTRACT(DAY FROM finish_time)")
    List<DayRow> selectWeekFinishedWeight(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 近 7 日碳减排（按流水 create_time 当日，type=1 获得） */
    @Select("SELECT EXTRACT(DAY FROM create_time) AS d, COALESCE(SUM(carbon_reduced), 0) AS amount "
            + "FROM biz_carbon_log WHERE deleted = 0 AND type = 1 "
            + "AND create_time >= #{from} AND create_time < #{to} GROUP BY EXTRACT(DAY FROM create_time)")
    List<DayRow> selectWeekCarbon(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 回收单按品类计数 Top5（名称字典 label 在服务层映射） */
    @Select("SELECT category, COUNT(*) AS cnt FROM biz_recycle_order "
            + "WHERE deleted = 0 GROUP BY category ORDER BY cnt DESC LIMIT 5")
    List<CategoryCount> selectCategoryCountTop5();

    @Data
    class RecycleStat {
        private Long totalOrders;
        private Long finishedOrders;
        private BigDecimal totalWeight;
        private Long todayOrders;
    }

    @Data
    class KitchenStat {
        private Long totalBags;
        private Long todayBags;
    }

    @Data
    class RepairStat {
        private Long totalOrders;
        private Long finishedOrders;
        private Long transferredOrders;
    }

    /** 月度聚合通用行：y/m + 计数 + 数值（重量/袋数/碳量复用 amount） */
    @Data
    class MonthRow {
        private Integer y;
        private Integer m;
        private Long cnt;
        private BigDecimal amount;
    }

    /** 周趋势聚合行：日号 d + 计数 + 数值（重量/碳量复用 amount） */
    @Data
    class DayRow {
        private Integer d;
        private Long cnt;
        private BigDecimal amount;
    }

    /** 品类计数行（工作台占比） */
    @Data
    class CategoryCount {
        private String category;
        private Long cnt;
    }
}

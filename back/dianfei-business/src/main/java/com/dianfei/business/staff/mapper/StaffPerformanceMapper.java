package com.dianfei.business.staff.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工绩效聚合 Mapper（G10）：按月/按日只读聚合 SQL（同库同源，语法兼容 MySQL 与 H2）。
 */
@Mapper
public interface StaffPerformanceMapper {

    /** 当月回收完成聚合：单数/实收重量/金额（status=3 完成，finish_time 当月） */
    @Select("SELECT COUNT(*) AS orders, COALESCE(SUM(weight), 0) AS weight, COALESCE(SUM(amount), 0) AS amount "
            + "FROM biz_recycle_order WHERE deleted = 0 AND staff_id = #{staffId} AND status = 3 "
            + "AND finish_time >= #{from} AND finish_time < #{to}")
    RecycleAgg selectRecycleAgg(@Param("staffId") Long staffId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 当月厨余任务完成聚合：单数/总重量（status=1 归档，finish_time 当月） */
    @Select("SELECT COUNT(*) AS orders, COALESCE(SUM(total_weight), 0) AS weight "
            + "FROM biz_kitchen_task WHERE deleted = 0 AND staff_id = #{staffId} AND status = 1 "
            + "AND finish_time >= #{from} AND finish_time < #{to}")
    KitchenAgg selectKitchenAgg(@Param("staffId") Long staffId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 当月维修完成单数（status=3 完成，finish_time 当月） */
    @Select("SELECT COUNT(*) FROM biz_repair_order WHERE deleted = 0 AND staff_id = #{staffId} AND status = 3 "
            + "AND finish_time >= #{from} AND finish_time < #{to}")
    long selectRepairCount(@Param("staffId") Long staffId,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 当月回收碳减排：按员工完成单回查碳流水（order_type='RECYCLE' + order_id） */
    @Select("SELECT COALESCE(SUM(cl.carbon_reduced), 0) FROM biz_carbon_log cl "
            + "INNER JOIN biz_recycle_order ro ON ro.id = cl.order_id AND ro.deleted = 0 "
            + "WHERE cl.deleted = 0 AND cl.order_type = 'RECYCLE' AND ro.staff_id = #{staffId} "
            + "AND ro.status = 3 AND ro.finish_time >= #{from} AND ro.finish_time < #{to}")
    BigDecimal selectRecycleCarbon(@Param("staffId") Long staffId,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 同片区在岗他人当月完成单数（三单源子查询，排名用） */
    @Select("SELECT s.id AS staff_id, "
            + "(SELECT COUNT(*) FROM biz_recycle_order r WHERE r.deleted = 0 AND r.staff_id = s.id "
            + "  AND r.status = 3 AND r.finish_time >= #{from} AND r.finish_time < #{to}) "
            + "+ (SELECT COUNT(*) FROM biz_repair_order rp WHERE rp.deleted = 0 AND rp.staff_id = s.id "
            + "  AND rp.status = 3 AND rp.finish_time >= #{from} AND rp.finish_time < #{to}) "
            + "+ (SELECT COUNT(*) FROM biz_kitchen_task k WHERE k.deleted = 0 AND k.staff_id = s.id "
            + "  AND k.status = 1 AND k.finish_time >= #{from} AND k.finish_time < #{to}) AS orders "
            + "FROM biz_staff s WHERE s.deleted = 0 AND s.area_id = #{areaId} AND s.id != #{staffId} "
            + "AND s.audit_status = 1 AND s.status = 1")
    List<StaffOrdersRow> selectAreaStaffOrders(@Param("areaId") Long areaId,
                                               @Param("staffId") Long staffId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** 当月好评率：该员工订单评价 rating>=4 占比（0-1 比例，无评价返回 null） */
    @Select("SELECT AVG(CASE WHEN rating >= 4 THEN 1 ELSE 0 END) "
            + "FROM biz_order_review WHERE staff_id = #{staffId} "
            + "AND create_time >= #{from} AND create_time < #{to}")
    BigDecimal selectPraiseRate(@Param("staffId") Long staffId,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 时间窗内按日完成单数（回收+维修+厨余三源 UNION ALL，缺失日由服务层补 0） */
    @Select("SELECT CAST(finish_time AS DATE) AS d, COUNT(*) AS c FROM ( "
            + "SELECT finish_time FROM biz_recycle_order WHERE deleted = 0 AND staff_id = #{staffId} "
            + "  AND status = 3 AND finish_time >= #{from} "
            + "UNION ALL "
            + "SELECT finish_time FROM biz_repair_order WHERE deleted = 0 AND staff_id = #{staffId} "
            + "  AND status = 3 AND finish_time >= #{from} "
            + "UNION ALL "
            + "SELECT finish_time FROM biz_kitchen_task WHERE deleted = 0 AND staff_id = #{staffId} "
            + "  AND status = 1 AND finish_time >= #{from} "
            + ") t GROUP BY CAST(finish_time AS DATE)")
    List<DayRow> selectDayOrders(@Param("staffId") Long staffId, @Param("from") LocalDateTime from);

    @Data
    class RecycleAgg {
        private Long orders;
        private BigDecimal weight;
        private BigDecimal amount;
    }

    @Data
    class KitchenAgg {
        private Long orders;
        private BigDecimal weight;
    }

    @Data
    class StaffOrdersRow {
        private Long staffId;
        private Long orders;
    }

    @Data
    class DayRow {
        private LocalDate d;
        private Long c;
    }
}

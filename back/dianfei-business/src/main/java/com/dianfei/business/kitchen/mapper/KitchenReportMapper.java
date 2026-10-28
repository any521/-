package com.dianfei.business.kitchen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.vo.AreaBagAggVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KitchenReportMapper extends BaseMapper<KitchenReport> {

    /** 时间窗内按片区聚合袋数（23 点后聚合任务 / 片区看板共用） */
    @Select("SELECT area_id AS areaId, SUM(bag_count) AS totalBags, COUNT(*) AS reportCount "
            + "FROM biz_kitchen_report WHERE deleted = 0 "
            + "AND report_time >= #{from} AND report_time < #{to} GROUP BY area_id")
    List<AreaBagAggVO> aggregateByArea(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

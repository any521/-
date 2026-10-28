package com.dianfei.business.diy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.diy.entity.DiyPost;
import com.dianfei.business.diy.vo.DiyAreaAggVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DiyPostMapper extends BaseMapper<DiyPost> {

    /** 点赞计数 +1（条件更新保证并发一致） */
    @Update("UPDATE biz_diy_post SET likes = likes + 1 WHERE id = #{id}")
    int incrLikes(@Param("id") Long id);

    /** 点赞计数 -1（likes > 0 条件防负） */
    @Update("UPDATE biz_diy_post SET likes = likes - 1 WHERE id = #{id} AND likes > 0")
    int decrLikes(@Param("id") Long id);

    /** 排行榜：回收实收重量 kg 按片区聚合（仅已完成单） */
    @Select("SELECT area_id AS areaId, IFNULL(SUM(weight), 0) AS value "
            + "FROM biz_recycle_order WHERE deleted = 0 AND status = 3 GROUP BY area_id")
    List<DiyAreaAggVO> aggregateRecycleKg();

    /** 排行榜：垃圾投放袋数按片区聚合（全部上报，含风控冻结复核单：投放事实成立） */
    @Select("SELECT area_id AS areaId, CAST(IFNULL(SUM(bag_count), 0) AS SIGNED) AS value "
            + "FROM biz_kitchen_report WHERE deleted = 0 GROUP BY area_id")
    List<DiyAreaAggVO> aggregateKitchenBags();

    /** 排行榜：碳减排 kg 按片区聚合（碳流水 JOIN 订单取片区；维修/兑换无片区归属不纳入） */
    @Select("SELECT COALESCE(r.area_id, k.area_id) AS areaId, IFNULL(SUM(l.carbon_reduced), 0) AS value "
            + "FROM biz_carbon_log l "
            + "LEFT JOIN biz_recycle_order r ON l.order_type = 'RECYCLE' AND l.order_id = r.id AND r.deleted = 0 "
            + "LEFT JOIN biz_kitchen_report k ON l.order_type = 'KITCHEN' AND l.order_id = k.id AND k.deleted = 0 "
            + "WHERE l.deleted = 0 AND l.type = 1 AND l.carbon_reduced > 0 "
            + "GROUP BY COALESCE(r.area_id, k.area_id)")
    List<DiyAreaAggVO> aggregateCarbonKg();

    /** 我的片区：最近一次非取消/异常回收单的片区（用户无绑定片区，以业务单推断） */
    @Select("SELECT area_id FROM biz_recycle_order WHERE deleted = 0 AND user_id = #{userId} "
            + "AND status IN (0, 1, 2, 3) ORDER BY id DESC LIMIT 1")
    Long findUserAreaFromRecycle(@Param("userId") Long userId);

    /** 我的片区兜底：最近一次垃圾投放上报的片区 */
    @Select("SELECT area_id FROM biz_kitchen_report WHERE deleted = 0 AND user_id = #{userId} "
            + "ORDER BY id DESC LIMIT 1")
    Long findUserAreaFromKitchen(@Param("userId") Long userId);
}

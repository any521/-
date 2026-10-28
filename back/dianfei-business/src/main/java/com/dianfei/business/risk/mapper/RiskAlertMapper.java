package com.dianfei.business.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.risk.entity.RiskAlert;
import com.dianfei.business.risk.vo.BrushRiskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RiskAlertMapper extends BaseMapper<RiskAlert> {

    /** 恶意刷单检测：时间窗内有效兑换单数达到阈值的用户（按单数倒序） */
    @Select("SELECT user_id AS userId, COUNT(*) AS orderCount, SUM(points_cost) AS totalPoints "
            + "FROM biz_mall_exchange WHERE deleted = 0 AND status <> 3 AND create_time >= #{since} "
            + "GROUP BY user_id HAVING COUNT(*) >= #{threshold} ORDER BY COUNT(*) DESC")
    List<BrushRiskVO> selectBrushSuspects(@Param("since") LocalDateTime since, @Param("threshold") int threshold);
}

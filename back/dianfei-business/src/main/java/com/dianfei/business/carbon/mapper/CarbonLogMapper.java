package com.dianfei.business.carbon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.carbon.entity.CarbonLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface CarbonLogMapper extends BaseMapper<CarbonLog> {

    /** 获得（type=1）减排量合计：G7 首页摘要 monthCarbon */
    @Select("SELECT COALESCE(SUM(carbon_reduced), 0) FROM biz_carbon_log "
            + "WHERE account_id = #{accountId} AND type = 1 AND create_time >= #{start} AND deleted = 0")
    BigDecimal sumMonthCarbon(@Param("accountId") Long accountId, @Param("start") LocalDateTime start);
}

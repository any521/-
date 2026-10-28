package com.dianfei.business.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.mall.entity.MallExchange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MallExchangeMapper extends BaseMapper<MallExchange> {

    @Update("UPDATE biz_mall_exchange SET status = #{cancelledStatus}, update_time = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = #{exchangedStatus} AND deleted = 0")
    int cancelIfPending(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("exchangedStatus") int exchangedStatus,
                        @Param("cancelledStatus") int cancelledStatus);
}

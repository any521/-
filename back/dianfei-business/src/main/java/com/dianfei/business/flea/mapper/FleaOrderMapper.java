package com.dianfei.business.flea.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.flea.entity.FleaOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FleaOrderMapper extends BaseMapper<FleaOrder> {

    @Update("UPDATE biz_flea_order SET status = #{cancelledStatus}, cancel_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = #{pendingStatus} AND deleted = 0")
    int cancelIfPending(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("pendingStatus") int pendingStatus,
                        @Param("cancelledStatus") int cancelledStatus);
}

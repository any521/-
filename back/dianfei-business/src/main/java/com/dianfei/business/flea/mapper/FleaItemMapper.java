package com.dianfei.business.flea.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.flea.entity.FleaItem;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FleaItemMapper extends BaseMapper<FleaItem> {

    /** 条件更新防双卖：仅 1在售 置 2已售出，返回 0 即已被并发下单/下架/违规 */
    @Update("UPDATE biz_flea_item SET status = #{soldStatus}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{onSaleStatus} AND deleted = 0")
    int markSold(@Param("id") Long id, @Param("onSaleStatus") int onSaleStatus, @Param("soldStatus") int soldStatus);

    /** 取消订单回在售：仅 2已售出 回 1在售（已下架/违规不复活） */
    @Update("UPDATE biz_flea_item SET status = #{onSaleStatus}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{soldStatus} AND deleted = 0")
    int backOnSale(@Param("id") Long id, @Param("onSaleStatus") int onSaleStatus, @Param("soldStatus") int soldStatus);

    /** 卖家已售出数聚合（列表/详情回填 soldCount） */
    @Select({"<script>",
            "SELECT user_id, COUNT(*) AS cnt FROM biz_flea_item ",
            "WHERE deleted = 0 AND status = #{soldStatus} AND user_id IN ",
            "<foreach collection='sellerIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>",
            " GROUP BY user_id",
            "</script>"})
    List<SoldCount> selectSoldCountBySellers(@Param("sellerIds") Collection<Long> sellerIds,
                                             @Param("soldStatus") int soldStatus);

    @Data
    class SoldCount {
        private Long userId;
        private Long cnt;
    }
}

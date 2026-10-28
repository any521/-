package com.dianfei.business.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.mall.entity.MallProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MallProductMapper extends BaseMapper<MallProduct> {

    /** 条件更新扣库存（防超卖）：仅上架且库存足够时成功，返回 0 即库存不足/已下架 */
    @Update("UPDATE biz_mall_product SET stock = stock - #{qty}, update_time = NOW() "
            + "WHERE id = #{id} AND status = 1 AND stock >= #{qty} AND deleted = 0")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消兑换返还库存 */
    @Update("UPDATE biz_mall_product SET stock = stock + #{qty}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}

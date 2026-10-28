package com.dianfei.business.carbon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.carbon.entity.CarbonAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface CarbonAccountMapper extends BaseMapper<CarbonAccount> {

    /** 行锁取账户：序列化同一账户的账本写入（余额更新 + 哈希链续接） */
    @Select("SELECT * FROM biz_carbon_account WHERE user_id = #{userId} AND deleted = 0 FOR UPDATE")
    CarbonAccount selectByUserIdForUpdate(Long userId);

    /** 减排量高于指定值的账户数（排名百分位计算） */
    @Select("SELECT COUNT(*) FROM biz_carbon_account WHERE deleted = 0 AND total_carbon_reduced > #{carbon}")
    long countHigherThan(@Param("carbon") BigDecimal carbon);

    /** 全站账户总数（排名百分位计算） */
    @Select("SELECT COUNT(*) FROM biz_carbon_account WHERE deleted = 0")
    long countAll();
}

package com.dianfei.system.mapper;

import com.dianfei.system.vo.StaffInfoVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 工作人员身份轻量查询（G1，C-O1 双身份契约）：dianfei-system 不依赖 dianfei-business，
 * 以只读 SQL 取 biz_staff 最新一条，仅暴露 ProfileVO 所需摘要字段，不改变模块依赖方向
 */
@Mapper
public interface StaffInfoMapper {

    /** biz_staff 最新一条（deleted=0 不限 auditStatus；未申请返回 null） */
    @Select("""
            SELECT id AS staffId, staff_type AS staffType, staff_name AS staffName,
                   area_id AS areaId, audit_status AS auditStatus, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d') AS hireDate
            FROM biz_staff
            WHERE user_id = #{userId} AND deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    StaffInfoVO selectLatestByUserId(Long userId);
}

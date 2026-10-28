package com.dianfei.business.support;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 系统用户只读映射（同库同源仅查询，不与 system 模块耦合）：
 * 管理端业务分页/摘要需要用户昵称、用户总数时使用（G5 垃圾投放记录 / G6 工作台摘要）。
 */
@Mapper
public interface SysUserBriefMapper {

    /** 批量取用户昵称（分页行回填，查不到的行不出现） */
    @Select({"<script>",
            "SELECT id, nickname FROM sys_user WHERE deleted = 0 AND id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<UserBrief> selectNicknameByIds(@Param("ids") Collection<Long> ids);

    /** 昵称/用户名模糊命中的用户 ID（keyword 过滤预查询） */
    @Select("SELECT id FROM sys_user WHERE deleted = 0 "
            + "AND (nickname LIKE CONCAT('%', #{keyword}, '%') OR username LIKE CONCAT('%', #{keyword}, '%'))")
    List<Long> selectIdsByKeyword(@Param("keyword") String keyword);

    /** 平台用户总数（deleted=0） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long countAll();

    /** 按角色取用户 ID（公告群发按目标端，AGENT 系统账号不参与） */
    @Select("SELECT id FROM sys_user WHERE deleted = 0 AND principal_type = 'HUMAN' AND role = #{role}")
    List<Long> selectIdsByRole(@Param("role") String role);

    /** 员工端用户 ID（RECYCLER/SANITATION/TECHNICIAN，AGENT 系统账号不参与） */
    @Select("SELECT id FROM sys_user WHERE deleted = 0 AND principal_type = 'HUMAN' "
            + "AND role IN ('RECYCLER', 'SANITATION', 'TECHNICIAN')")
    List<Long> selectStaffIds();

    /** 全部真人用户 ID（排除 AGENT 系统账号） */
    @Select("SELECT id FROM sys_user WHERE deleted = 0 AND principal_type = 'HUMAN'")
    List<Long> selectAllHumanIds();

    @Data
    class UserBrief {
        private Long id;
        private String nickname;
    }
}

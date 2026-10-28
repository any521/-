package com.dianfei.ai.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.ai.mcp.entity.McpAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface McpAuditMapper extends BaseMapper<McpAudit> {

    /** 数据库条件更新抢占确认单，保证同一确认单只允许一个执行者。 */
    @Update("UPDATE biz_mcp_audit SET confirm_state = 3, update_time = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND need_confirm = 1 "
            + "AND confirm_state = 0 AND deleted = 0")
    int claimPending(@Param("id") Long id, @Param("userId") Long userId);
}

package com.dianfei.business.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianfei.business.agent.entity.AgentDecision;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 决策单 Mapper（C5，biz_agent_decision）
 */
@Mapper
public interface AgentDecisionMapper extends BaseMapper<AgentDecision> {
}

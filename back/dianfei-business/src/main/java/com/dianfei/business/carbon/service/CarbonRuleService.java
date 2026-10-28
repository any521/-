package com.dianfei.business.carbon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.dto.CarbonRuleSaveDTO;
import com.dianfei.business.carbon.entity.CarbonRule;
import com.dianfei.business.carbon.mapper.CarbonRuleMapper;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 碳积分规则配置（B2）：orderType+category 唯一，upsert 语义；
 * 新增字典品类 + 报价 + 规则即可零代码下单获分（对齐 09.3）
 */
@Service
@RequiredArgsConstructor
public class CarbonRuleService {

    private final CarbonRuleMapper ruleMapper;

    public List<CarbonRule> list(String orderType) {
        return ruleMapper.selectList(new LambdaQueryWrapper<CarbonRule>()
                .eq(orderType != null && !orderType.isBlank(), CarbonRule::getOrderType, orderType)
                .orderByAsc(CarbonRule::getOrderType)
                .orderByAsc(CarbonRule::getCategory));
    }

    @Transactional
    public CarbonRule save(CarbonRuleSaveDTO dto) {
        if (OrderTypeEnum.of(dto.getOrderType()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "orderType 不合法");
        }
        if (dto.getPointsPerKg() == null && dto.getPointsPerBag() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "points_per_kg 与 points_per_bag 至少配置一项");
        }
        CarbonRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<CarbonRule>()
                .eq(CarbonRule::getOrderType, dto.getOrderType())
                .eq(CarbonRule::getCategory, dto.getCategory()));
        if (rule == null) {
            rule = new CarbonRule();
        }
        rule.setRuleCode(dto.getRuleCode());
        rule.setOrderType(dto.getOrderType());
        rule.setCategory(dto.getCategory());
        rule.setPointsPerKg(dto.getPointsPerKg());
        rule.setPointsPerBag(dto.getPointsPerBag());
        rule.setCarbonPerKg(dto.getCarbonPerKg());
        rule.setCarbonPerBag(dto.getCarbonPerBag());
        rule.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return rule;
    }
}

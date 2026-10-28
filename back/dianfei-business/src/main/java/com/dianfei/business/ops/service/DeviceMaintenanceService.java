package com.dianfei.business.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.ops.dto.MaintenanceSaveDTO;
import com.dianfei.business.ops.entity.DeviceMaintenance;
import com.dianfei.business.ops.mapper.DeviceMaintenanceMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 车辆设备运维登记（C4）：保养/维修/年检流水，登记后不可改（留痕），仅可查
 */
@Service
public class DeviceMaintenanceService extends ServiceImpl<DeviceMaintenanceMapper, DeviceMaintenance> {

    /** 登记运维记录 */
    @Transactional
    public DeviceMaintenance register(Long operatorId, MaintenanceSaveDTO dto) {
        if (dto.getTargetType() != DeviceMaintenance.TARGET_VEHICLE
                && dto.getTargetType() != DeviceMaintenance.TARGET_DEVICE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "运维对象类型不合法");
        }
        DeviceMaintenance maintenance = new DeviceMaintenance();
        maintenance.setTargetType(dto.getTargetType());
        maintenance.setTargetNo(dto.getTargetNo());
        maintenance.setContent(dto.getContent());
        maintenance.setCost(dto.getCost() == null ? BigDecimal.ZERO : dto.getCost());
        maintenance.setMaintDate(dto.getMaintDate());
        maintenance.setOperatorId(operatorId);
        save(maintenance);
        return maintenance;
    }

    /** 运维记录分页（对象类型/编号过滤，近期优先） */
    public PageVO<DeviceMaintenance> pageAdmin(Integer targetType, String targetNo, int pageNum, int pageSize) {
        LambdaQueryWrapper<DeviceMaintenance> wrapper = new LambdaQueryWrapper<DeviceMaintenance>()
                .eq(targetType != null, DeviceMaintenance::getTargetType, targetType)
                .like(targetNo != null && !targetNo.isBlank(), DeviceMaintenance::getTargetNo, targetNo)
                .orderByDesc(DeviceMaintenance::getMaintDate).orderByDesc(DeviceMaintenance::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }
}

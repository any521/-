package com.dianfei.business.kitchen.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.kitchen.dto.VehicleSaveDTO;
import com.dianfei.business.kitchen.entity.Vehicle;
import com.dianfei.business.kitchen.mapper.VehicleMapper;
import com.dianfei.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 清运车辆配置（B4 后台）：vehicle_no 唯一 upsert；
 * 车辆池供聚合任务 suggestVehicle 派车建议与后台改派使用
 */
@Service
public class VehicleService extends ServiceImpl<VehicleMapper, Vehicle> {

    /** 车辆列表（含停用，供配置页与改派下拉） */
    public List<Vehicle> listAll(Long areaId) {
        return lambdaQuery()
                .eq(areaId != null, Vehicle::getAreaId, areaId)
                .orderByDesc(Vehicle::getStatus)
                .orderByAsc(Vehicle::getVehicleNo)
                .list();
    }

    /** 车牌号幂等 upsert：同号车辆更新片区/容量/状态，新号新增 */
    @Transactional
    public Vehicle saveByVehicleNo(VehicleSaveDTO dto) {
        Vehicle existed = lambdaQuery().eq(Vehicle::getVehicleNo, dto.getVehicleNo()).one();
        if (existed != null && dto.getId() != null && !existed.getId().equals(dto.getId())) {
            throw new BusinessException("车牌号 " + dto.getVehicleNo() + " 已被其他车辆占用");
        }
        Vehicle vehicle = existed != null ? existed
                : (dto.getId() != null ? requireVehicle(dto.getId()) : new Vehicle());
        vehicle.setVehicleNo(dto.getVehicleNo());
        vehicle.setAreaId(dto.getAreaId());
        vehicle.setCapacity(dto.getCapacity());
        vehicle.setStatus(dto.getStatus());
        saveOrUpdate(vehicle);
        return vehicle;
    }

    public Vehicle requireVehicle(Long id) {
        Vehicle vehicle = getById(id);
        if (vehicle == null) {
            throw new BusinessException("车辆不存在");
        }
        return vehicle;
    }
}

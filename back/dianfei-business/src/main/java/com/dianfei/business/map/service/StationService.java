package com.dianfei.business.map.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.business.map.dto.StationSaveDTO;
import com.dianfei.business.map.entity.BizStation;
import com.dianfei.business.map.mapper.BizStationMapper;
import com.dianfei.business.map.vo.NearestStationVO;
import com.dianfei.business.map.vo.RouteVO;
import com.dianfei.business.map.vo.StationVO;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 垃圾站点（onsite04）：用户端列表/最近站点+最优路线；管理端 CRUD（V43 菜单驱动）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationService {

    private final BizStationMapper stationMapper;
    private final AmapProxyService amapProxyService;

    /** 站点列表：type 可选（RECYCLE/KITCHEN/COLLECT），仅开放站点；
     * 传入 lng/lat/radiusKm（三者齐全且 radiusKm>0）时按球面距离过滤，仅返回半径内站点（全国种子数据 5600+ 条，用户端必须带坐标检索） */
    public List<StationVO> list(String type, BigDecimal lng, BigDecimal lat, Double radiusKm) {
        java.util.stream.Stream<BizStation> stream = stationMapper.selectList(new LambdaQueryWrapper<BizStation>()
                        .eq(BizStation::getStatus, 1)
                        .eq(type != null && !type.isBlank(), BizStation::getStationType, type)
                        .orderByAsc(BizStation::getId))
                .stream();
        if (lng != null && lat != null && radiusKm != null && radiusKm > 0) {
            double r = radiusKm * 1000;
            stream = stream.filter(s -> s.getLng() != null && s.getLat() != null
                    && haversineMeters(lat.doubleValue(), lng.doubleValue(),
                    s.getLat().doubleValue(), s.getLng().doubleValue()) <= r);
        }
        return stream
                .map(s -> {
                    StationVO vo = BeanUtil.copyProperties(s, StationVO.class);
                    // 雪花 Long 转 String 防前端精度丢失
                    vo.setId(String.valueOf(s.getId()));
                    return vo;
                })
                .toList();
    }

    /** 管理端分页：名称模糊 + 类型筛选 */
    public PageVO<BizStation> page(int pageNum, int pageSize, String keyword, String stationType) {
        LambdaQueryWrapper<BizStation> wrapper = new LambdaQueryWrapper<BizStation>()
                .like(keyword != null && !keyword.isBlank(), BizStation::getName, keyword)
                .eq(stationType != null && !stationType.isBlank(), BizStation::getStationType, stationType)
                .orderByDesc(BizStation::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    private Page<BizStation> page(Page<BizStation> page, LambdaQueryWrapper<BizStation> wrapper) {
        return stationMapper.selectPage(page, wrapper);
    }

    /** 新增/编辑站点（管理端 AddressPicker 选点回填坐标） */
    @Transactional
    public BizStation saveStation(StationSaveDTO dto) {
        BizStation station = dto.getId() == null ? new BizStation() : stationMapper.selectById(dto.getId());
        if (station == null) {
            throw new BusinessException("站点不存在");
        }
        station.setName(dto.getName());
        station.setStationType(dto.getStationType());
        station.setLng(dto.getLng());
        station.setLat(dto.getLat());
        station.setAddress(dto.getAddress());
        station.setOpenHours(dto.getOpenHours());
        if (dto.getStatus() != null) {
            station.setStatus(dto.getStatus());
        }
        if (station.getStatus() == null) {
            station.setStatus(1);
        }
        if (station.getId() == null) {
            stationMapper.insert(station);
        } else {
            stationMapper.updateById(station);
        }
        return station;
    }

    /**
     * 最近站点 + 最优路线：直线距离取最近开放站点，再调高德步行路线规划；
     * 路线规划不可用（server_key 未开通等）降级为直线距离 + 无折线（前端画直线）
     */
    public NearestStationVO nearest(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            throw new BusinessException("经纬度不能为空");
        }
        List<BizStation> stations = stationMapper.selectList(new LambdaQueryWrapper<BizStation>()
                .eq(BizStation::getStatus, 1));
        if (stations.isEmpty()) {
            return null;
        }

        // 直线距离（Haversine，米）取最近站点
        BizStation best = null;
        double bestDist = Double.MAX_VALUE;
        for (BizStation s : stations) {
            double d = haversineMeters(lat.doubleValue(), lng.doubleValue(),
                    s.getLat().doubleValue(), s.getLng().doubleValue());
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }

        NearestStationVO vo = BeanUtil.copyProperties(best, NearestStationVO.class);
        vo.setId(String.valueOf(best.getId()));
        vo.setDistance(BigDecimal.valueOf(Math.round(bestDist)));

        // 最优路线：步行规划；失败降级直线
        try {
            RouteVO route = amapProxyService.route(lng, lat, best.getLng(), best.getLat(), "walking");
            vo.setDistance(route.getDistance());
            vo.setDuration(route.getDuration());
            vo.setPolyline(route.getPolyline());
        } catch (Exception e) {
            log.warn("[最近站点] 路线规划不可用，降级直线距离 msg={}", e.getMessage());
        }
        return vo;
    }

    /** 逻辑删除站点 */
    public void removeById(Long id) {
        stationMapper.deleteById(id);
    }

    /** Haversine 球面距离（米） */
    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}

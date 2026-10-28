package com.dianfei.business.staff.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.map.service.AmapProxyService;
import com.dianfei.business.map.vo.GeocodeVO;
import com.dianfei.business.staff.dto.AreaSaveDTO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.mapper.BizAreaMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 片区管理（B1）：CRUD + 启停 + 自动创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaService extends ServiceImpl<BizAreaMapper, BizArea> {

    private final AmapProxyService amapProxyService; // 注入高德代理（真实逆地理 + Redis 缓存）

    /**
     * 根据经纬度自动创建或匹配片区（D0）：
     * 1. 调用高德逆地理获取 省/市/县/镇/村(小区/学校/公司) 结构化地址
     * 2. 检查同名片区是否存在，存在则返回 ID
     * 3. 不存在则自动生成新片区
     */
    public Long createOrMatchAreaByLocation(Double lng, Double lat) {
        if (lng == null || lat == null) {
            throw new BusinessException("经纬度不能为空");
        }

        // Step 1: 真实逆地理（AmapProxyService 带 10min Redis 缓存，防配额耗尽）
        // 🎯 降级：server_key 未开通逆地理（SERVICE_NOT_AVAILABLE）时不抛 5001，改用坐标命名片区
        GeocodeVO geo = null;
        try {
            geo = amapProxyService.regeo(BigDecimal.valueOf(lng), BigDecimal.valueOf(lat));
        } catch (Exception e) {
            log.warn("[片区自动创建] 逆地理不可用，降级为坐标命名 lng={} lat={} msg={}", lng, lat, e.getMessage());
        }

        String queryName = null;
        String address = null;
        if (geo != null) {
            String province = blankToEmpty(geo.getProvince());
            String city = blankToEmpty(geo.getCity());
            if (city.isEmpty()) city = province; // 直辖市降级
            String district = blankToEmpty(geo.getDistrict());
            String township = blankToEmpty(geo.getTownship());
            String village = blankToEmpty(geo.getVillage());
            if (province.isEmpty() && district.isEmpty()) {
                geo = null; // 解析不出层级 → 走坐标命名
            } else {
                // Step 2: 片区名 = 省/市/县/镇/村（纯行政区划层级，不拼小区/兴趣点）；同名已存在则直接复用
                queryName = buildAreaName(province, city, district, township, village);
                address = geo.getFormattedAddress();
            }
        }
        if (geo == null) {
            // 🎯 降级命名：坐标保留 4 位小数，同一点重复确认命中同一片区
            queryName = String.format("片区-%.4f,%.4f", lng, lat);
        }
        BizArea match = getOne(new LambdaQueryWrapper<BizArea>().eq(BizArea::getAreaName, queryName).last("LIMIT 1"));
        if (match != null && match.getStatus() != null && match.getStatus() == 1) {
            return match.getId();
        }

        // Step 3: 没有同名片区 → 自动创建
        BizArea area = new BizArea();
        area.setId(generateNewId());
        area.setAreaName(queryName);
        area.setAddress(address);
        area.setLng(BigDecimal.valueOf(lng));
        area.setLat(BigDecimal.valueOf(lat));
        area.setCleanThreshold(100);
        // 自动创建片区无户数录入入口，给默认值避免风控户均基数除零（管理员可在片区列表修正）
        area.setHouseholdCount(300);
        area.setStatus(1);
        save(area);

        log.info("[片区自动创建] id={} name={}", area.getId(), area.getAreaName());
        return area.getId();
    }

    private String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** 构建片区名称（省/市/县/镇/村，去重相邻同名层级；不拼小区/兴趣点，保持纯行政区划） */
    private String buildAreaName(String province, String city, String district, String township, String village) {
        List<String> parts = new ArrayList<>();
        for (String p : new String[]{province, city, district, township, village}) {
            if (p != null && !p.isEmpty() && (parts.isEmpty() || !parts.get(parts.size() - 1).equals(p))) {
                parts.add(p);
            }
        }
        return String.join("", parts);
    }

    /** 生成新 ID（雪花 ID：全局唯一、并发安全；不再用 MAX(id)+1——该策略在逻辑删除记录存在时会复用主键报 Duplicate entry） */
    private long generateNewId() {
        return IdUtil.getSnowflakeNextId();
    }

    public PageVO<BizArea> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<BizArea> wrapper = new LambdaQueryWrapper<BizArea>()
                .like(keyword != null && !keyword.isBlank(), BizArea::getAreaName, keyword)
                .orderByDesc(BizArea::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Transactional
    public BizArea saveArea(AreaSaveDTO dto) {
        BizArea area = dto.getId() == null ? new BizArea() : getById(dto.getId());
        if (area == null) {
            throw new BusinessException("片区不存在");
        }
        area.setAreaName(dto.getAreaName());
        area.setAddress(dto.getAddress());
        if (dto.getLng() != null) {
            area.setLng(dto.getLng());
        }
        if (dto.getLat() != null) {
            area.setLat(dto.getLat());
        }
        if (dto.getCleanThreshold() != null) {
            area.setCleanThreshold(dto.getCleanThreshold());
        }
        if (dto.getHouseholdCount() != null) {
            area.setHouseholdCount(dto.getHouseholdCount());
        }
        if (dto.getStatus() != null) {
            area.setStatus(dto.getStatus());
        }
        if (area.getCleanThreshold() == null) {
            area.setCleanThreshold(100);
        }
        if (area.getHouseholdCount() == null) {
            area.setHouseholdCount(0);
        }
        if (area.getStatus() == null) {
            area.setStatus(1);
        }
        saveOrUpdate(area);
        return area;
    }

    /** 校验片区存在且启用 */
    public BizArea requireEnabled(Long areaId) {
        BizArea area = getById(areaId);
        if (area == null || area.getStatus() == null || area.getStatus() != 1) {
            throw new BusinessException("片区不存在或已停用");
        }
        return area;
    }
}

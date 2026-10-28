package com.dianfei.business.staff.controller;

import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端 · 片区 API（D0 自动创建）
 */
@Tag(name = "用户端-片区")
@RestController
@RequestMapping("/api/staff/areas")
@RequiredArgsConstructor
public class UserAreaController {

    private final AreaService areaService;

    @Operation(summary = "获取所有启用片区列表（供地址选择器动态加载）")
    @GetMapping
    public Result<List<BizArea>> list() {
        List<BizArea> areas = areaService.lambdaQuery()
                .eq(BizArea::getStatus, 1)
                .orderByAsc(BizArea::getId)
                .list();
        return Result.success(areas);
    }

    @Operation(summary = "根据经纬度自动创建/匹配片区（D0）")
    @PostMapping("/auto-create")
    public Result<AutoCreateAreaVO> autoCreate(@RequestBody AutoCreateDTO dto) {
        Long areaId = areaService.createOrMatchAreaByLocation(dto.getLng(), dto.getLat());
        BizArea area = areaService.getById(areaId);
        AutoCreateAreaVO vo = new AutoCreateAreaVO();
        vo.setId(areaId);
        vo.setName(area != null ? area.getAreaName() : "");
        return Result.success(vo);
    }

    /** 自动创建入参 */
    public static class AutoCreateDTO {
        private Double lng;
        private Double lat;

        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
    }

    /** 自动创建返回 */
    public static class AutoCreateAreaVO {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

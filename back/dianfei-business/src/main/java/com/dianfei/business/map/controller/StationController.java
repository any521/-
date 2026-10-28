package com.dianfei.business.map.controller;

import com.dianfei.business.map.service.StationService;
import com.dianfei.business.map.vo.NearestStationVO;
import com.dianfei.business.map.vo.StationVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 回收站点（onsite04）：GET /api/stations 全量开放站点（≤50 条）；
 * 管理端站点 CRUD 归管理端批次（本批次种子数据 + SQL 维护）。
 */
@Tag(name = "回收站点")
@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    @Operation(summary = "回收站点列表（type 可选 RECYCLE/KITCHEN/COLLECT；lng/lat/radiusKm 齐全时按半径 km 过滤）")
    @GetMapping
    public Result<List<StationVO>> list(@RequestParam(required = false) String type,
                                        @RequestParam(required = false) java.math.BigDecimal lng,
                                        @RequestParam(required = false) java.math.BigDecimal lat,
                                        @RequestParam(required = false) Double radiusKm) {
        return Result.success(stationService.list(type, lng, lat, radiusKm));
    }

    @Operation(summary = "最近垃圾站点 + 最优步行路线（用户端垃圾投放页地图展示）")
    @GetMapping("/nearest")
    public Result<NearestStationVO> nearest(@RequestParam java.math.BigDecimal lng,
                                            @RequestParam java.math.BigDecimal lat) {
        return Result.success(stationService.nearest(lng, lat));
    }
}

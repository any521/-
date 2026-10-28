package com.dianfei.business.map.controller;

import com.dianfei.business.map.dto.StationSaveDTO;
import com.dianfei.business.map.entity.BizStation;
import com.dianfei.business.map.service.StationService;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · 垃圾站点管理（V43）：管理员地图选点设置站点，用户端据此展示最近站点与最优路线
 */
@Tag(name = "后台-垃圾站点管理")
@RestController
@RequestMapping("/api/admin/stations")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminStationController {

    private final StationService stationService;

    @Operation(summary = "站点分页查询")
    @GetMapping
    public Result<PageVO<BizStation>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String stationType) {
        return Result.success(stationService.page(pageNum, pageSize, keyword, stationType));
    }

    @Operation(summary = "新增站点")
    @OperLog(value = "新增站点", module = "站点管理")
    @PostMapping
    public Result<BizStation> create(@Valid @RequestBody StationSaveDTO dto) {
        dto.setId(null);
        return Result.success(stationService.saveStation(dto));
    }

    @Operation(summary = "修改站点")
    @OperLog(value = "修改站点", module = "站点管理")
    @PutMapping("/{id}")
    public Result<BizStation> update(@PathVariable Long id, @Valid @RequestBody StationSaveDTO dto) {
        dto.setId(id);
        return Result.success(stationService.saveStation(dto));
    }

    @Operation(summary = "删除站点（逻辑删除）")
    @OperLog(value = "删除站点", module = "站点管理")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        stationService.removeById(id);
        return Result.success();
    }
}

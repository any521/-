package com.dianfei.business.kitchen.controller;

import com.dianfei.business.kitchen.dto.KitchenTaskPublishDTO;
import com.dianfei.business.kitchen.dto.VehicleSaveDTO;
import com.dianfei.business.kitchen.entity.KitchenTask;
import com.dianfei.business.kitchen.entity.Vehicle;
import com.dianfei.business.kitchen.service.KitchenReportService;
import com.dianfei.business.kitchen.service.KitchenTaskService;
import com.dianfei.business.kitchen.service.VehicleService;
import com.dianfei.business.kitchen.vo.KitchenRecordVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 后台 · 厨余清运管理（B4）：清运任务查询/改派、聚合手动补跑（23:10 定时失败兜底）、车辆配置。
 * 改派限本片区在岗环卫员，@OperLog 留痕；聚合 uk_task_area_date 幂等可重复触发
 */
@Tag(name = "后台-厨余清运管理")
@RestController
@RequestMapping("/api/admin/kitchen")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminKitchenController {

    private final KitchenReportService reportService;
    private final KitchenTaskService taskService;
    private final VehicleService vehicleService;

    @Operation(summary = "垃圾投放记录分页（片区/状态/关键词）")
    @GetMapping("/records")
    public Result<PageVO<KitchenRecordVO>> pageRecords(@Valid PageDTO page,
                                                       @RequestParam(required = false) Long areaId,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false) String keyword) {
        return Result.success(reportService.pageAdmin(page, areaId, status, keyword));
    }

    @Operation(summary = "清运任务分页（日期/片区/状态过滤）")
    @GetMapping("/tasks")
    public Result<PageVO<KitchenTask>> pageTasks(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taskDate,
                                                 @RequestParam(required = false) Long areaId,
                                                 @RequestParam(required = false) Integer status) {
        return Result.success(taskService.pageAdmin(taskDate, areaId, status, pageNum, pageSize));
    }

    @Operation(summary = "改派车辆/环卫员（仅待执行任务，限本片区环卫员）")
    @OperLog(value = "清运任务改派", module = "厨余清运管理")
    @PutMapping("/tasks/{id}/reassign")
    public Result<KitchenTask> reassign(@PathVariable Long id,
                                        @RequestParam(required = false) Long vehicleId,
                                        @RequestParam(required = false) Long staffId) {
        return Result.success(taskService.reassign(id, vehicleId, staffId));
    }

    @Operation(summary = "手动触发聚合（定时任务失败兜底，幂等可重跑，返回新建任务数）")
    @OperLog(value = "手动触发厨余聚合", module = "厨余清运管理")
    @PostMapping("/aggregate")
    public Result<Integer> aggregate(@RequestParam
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return Result.success(taskService.aggregate(reportDate));
    }

    @Operation(summary = "发布清运任务（管理员指定片区/日期/袋数直接发布，重复发布拦截）")
    @OperLog(value = "发布清运任务", module = "厨余清运管理")
    @PostMapping("/tasks/publish")
    public Result<KitchenTask> publish(@Valid @RequestBody KitchenTaskPublishDTO dto) {
        return Result.success(taskService.publish(dto));
    }

    @Operation(summary = "车辆列表（含停用，供配置页与改派下拉）")
    @GetMapping("/vehicles")
    public Result<List<Vehicle>> listVehicles(@RequestParam(required = false) Long areaId) {
        return Result.success(vehicleService.listAll(areaId));
    }

    @Operation(summary = "配置车辆（vehicle_no 幂等 upsert）")
    @OperLog(value = "配置清运车辆", module = "厨余清运管理")
    @PutMapping("/vehicles")
    public Result<Vehicle> saveVehicle(@Valid @RequestBody VehicleSaveDTO dto) {
        return Result.success(vehicleService.saveByVehicleNo(dto));
    }
}

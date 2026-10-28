package com.dianfei.business.kitchen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.kitchen.dto.KitchenTaskFinishDTO;
import com.dianfei.business.kitchen.dto.KitchenTaskPublishDTO;
import com.dianfei.business.kitchen.entity.KitchenReport;
import com.dianfei.business.kitchen.entity.KitchenTask;
import com.dianfei.business.kitchen.entity.Vehicle;
import com.dianfei.business.kitchen.mapper.KitchenTaskMapper;
import com.dianfei.business.kitchen.mapper.VehicleMapper;
import com.dianfei.business.kitchen.vo.AreaBagAggVO;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 厨余清运任务（B4）：
 * 聚合——每晚 23 点后汇总当日各片区袋数，达 clean_threshold 生成次日任务与派车建议（uk 幂等）；
 * 归档——环卫员完成填报（总重量/车号/终点/凭证）→ 对上报日已通过审核的上报单发分兜底（账本幂等）；
 * 积分主体在投放审核通过时发放（KitchenReportService.audit），此处仅幂等补发防漏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenTaskService extends ServiceImpl<KitchenTaskMapper, KitchenTask> {

    /** 垃圾投放按袋计分，规则表 category 约定 DEFAULT（品类字典驱动，对齐 09.3） */
    public static final String KITCHEN_CATEGORY = "DEFAULT";

    private final KitchenReportService reportService;
    private final AreaService areaService;
    private final StaffService staffService;
    private final CarbonLedgerOps carbonLedger;
    private final VehicleMapper vehicleMapper;
    private final MessageSender messageSender;
    private final Clock clock;

    // ==================== 聚合（每晚 23 点后定时 / 后台手动触发） ====================

    /**
     * 聚合 reportDate 当日上报 → 达阈值片区生成次日清运任务与派车建议。
     * uk_task_area_date 幂等：已存在任务的片区跳过，可安全重跑；返回本轮新建任务数
     */
    @Transactional
    public int aggregate(LocalDate reportDate) {
        LocalDate taskDate = reportDate.plusDays(1);
        List<AreaBagAggVO> aggs = reportService.getBaseMapper()
                .aggregateByArea(reportDate.atStartOfDay(), taskDate.atStartOfDay());
        int created = 0;
        for (AreaBagAggVO agg : aggs) {
            BizArea area = areaService.getById(agg.getAreaId());
            if (area == null || area.getStatus() == null || area.getStatus() != 1) {
                continue;
            }
            int threshold = area.getCleanThreshold() == null ? Integer.MAX_VALUE : area.getCleanThreshold();
            if (agg.getTotalBags() < threshold) {
                log.info("[Kitchen] 片区 {} 当日 {} 袋未达阈值 {}，不生成清运任务",
                        area.getAreaName(), agg.getTotalBags(), threshold);
                continue;
            }
            if (lambdaQuery().eq(KitchenTask::getAreaId, area.getId())
                    .eq(KitchenTask::getTaskDate, taskDate).exists()) {
                continue;
            }
            KitchenTask task = new KitchenTask();
            task.setAreaId(area.getId());
            task.setTaskDate(taskDate);
            task.setTotalBags(agg.getTotalBags().intValue());
            Vehicle vehicle = suggestVehicle(area.getId(), agg.getTotalBags());
            task.setVehicleId(vehicle == null ? null : vehicle.getId());
            task.setStatus(KitchenTask.STATUS_PENDING);
            save(task);
            created++;
            notifyAreaSanitation(area, task, vehicle);
        }
        log.info("[Kitchen] {} 聚合完成：{} 片区上报，新建清运任务 {} 个", reportDate, aggs.size(), created);
        return created;
    }

    /**
     * 派车建议：优先本片区/机动（area_id 为空）可用车辆中容量够装的最小车；
     * 无一辆装得下时给容量最大车（分趟清运），无可用车辆返回 null 由后台人工派车
     */
    private Vehicle suggestVehicle(Long areaId, long totalBags) {
        List<Vehicle> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getStatus, 1)
                .and(w -> w.eq(Vehicle::getAreaId, areaId).or(ww -> ww.isNull(Vehicle::getAreaId))));
        return vehicles.stream()
                .filter(v -> v.getCapacity() != null && v.getCapacity() >= totalBags)
                .min((a, b) -> Integer.compare(a.getCapacity(), b.getCapacity()))
                .orElseGet(() -> vehicles.stream()
                        .filter(v -> v.getCapacity() != null)
                        .max((a, b) -> Integer.compare(a.getCapacity(), b.getCapacity()))
                        .orElse(null));
    }

    /** 通知片区在岗环卫员次日清运 */
    private void notifyAreaSanitation(BizArea area, KitchenTask task, Vehicle vehicle) {
        for (BizStaff staff : staffService.listOnDuty(area.getId(), StaffTypeEnum.SANITATION)) {
            messageSender.send(staff.getUserId(), MessageSender.TYPE_DISPATCHED,
                    "明日厨余清运任务", area.getAreaName() + " " + task.getTaskDate() + " 需清运 "
                            + task.getTotalBags() + " 袋" + (vehicle == null ? "，暂无可用车辆，请联系后台派车"
                            : "，建议派车 " + vehicle.getVehicleNo()));
        }
    }

    // ==================== 员工端（环卫员） ====================

    /** 员工任务列表（在岗员工可查看）：全量清运任务，待执行优先；areaName 联表填充真实片区名（前端按当前片区分类过滤） */
    public List<KitchenTask> myTasks(Long staffUserId) {
        requireStaff(staffUserId);
        LambdaQueryWrapper<KitchenTask> wrapper = new LambdaQueryWrapper<KitchenTask>()
                .orderByAsc(KitchenTask::getStatus)
                .orderByDesc(KitchenTask::getTaskDate);
        return fillAreaName(list(wrapper));
    }

    /**
     * 完成填报归档（PUT /api/staff/kitchen/tasks/{id}/finish）：0待执行 → 1已完成。
     * 录总重量/车号/终点/凭证 → 对上报日已通过审核的上报单统一发分兜底（bizKey 幂等，
     * 审核通过已发分时重复归档不重复发；待审核/驳回/已复核单跳过）
     */
    @Transactional
    public KitchenTask finish(Long taskId, Long staffUserId, KitchenTaskFinishDTO dto) {
        BizStaff staff = requireSanitation(staffUserId);
        KitchenTask task = requireTask(taskId);
        if (!Objects.equals(task.getAreaId(), staff.getAreaId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅可归档本片区清运任务");
        }
        if (task.getStatus() != KitchenTask.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "该清运任务已归档");
        }

        task.setStaffId(staff.getId());
        task.setStatus(KitchenTask.STATUS_FINISHED);
        task.setTotalWeight(dto.getTotalWeight());
        task.setVehicleNo(dto.getVehicleNo());
        task.setDestination(dto.getDestination());
        task.setProofUrl(dto.getProofUrl());
        task.setFinishTime(LocalDateTime.now(clock));
        updateById(task);
        fillAreaName(List.of(task));

        // 上报日 = 任务日前一天；逐单发分入哈希链，账本 bizKey 幂等兜底
        LocalDate reportDate = task.getTaskDate().minusDays(1);
        for (KitchenReport report : reportService.listAreaReports(task.getAreaId(), reportDate)) {
            settleReport(report);
        }
        return task;
    }

    /** 单笔上报结算：仅已通过单发分（账本幂等，审核通过已发时静默跳过）；其余状态不结算 */
    private void settleReport(KitchenReport report) {
        if (report.getStatus() != KitchenReport.STATUS_APPROVED) {
            return;
        }
        CarbonGrantCmd cmd = CarbonGrantCmd.builder()
                .userId(report.getUserId())
                .orderType(OrderTypeEnum.KITCHEN)
                .orderId(report.getId())
                .category(KITCHEN_CATEGORY)
                .quantity(report.getBagCount())
                .build();
        CarbonLedgerResult result = carbonLedger.grant(cmd);
        if (!result.isDuplicated()) {
            messageSender.send(report.getUserId(), MessageSender.TYPE_POINTS_ARRIVED,
                    "垃圾投放积分到账", "投放 " + report.getBagCount() + " 袋，+"
                            + result.getPoints() + " 分，当前余额 " + result.getBalanceAfter());
        }
    }

    // ==================== 后台 ====================

    /**
     * 管理员手动发布清运任务（POST /api/admin/kitchen/tasks/publish）：
     * 指定片区/清运日期/袋数直接生成待执行任务，派车建议 + 通知片区环卫员；
     * uk_task_area_date 幂等：同片区同日期已存在则拒绝，避免与定时聚合/重复点击重复生成
     */
    @Transactional
    public KitchenTask publish(KitchenTaskPublishDTO dto) {
        BizArea area = areaService.requireEnabled(dto.getAreaId());
        LocalDate today = LocalDate.now(clock);
        if (dto.getTaskDate().isBefore(today)) {
            throw new BusinessException("清运日期不能早于今天");
        }
        if (lambdaQuery().eq(KitchenTask::getAreaId, area.getId())
                .eq(KitchenTask::getTaskDate, dto.getTaskDate()).exists()) {
            throw new BusinessException("该片区该日期已有清运任务，请改期或直接改派");
        }
        KitchenTask task = new KitchenTask();
        task.setAreaId(area.getId());
        task.setTaskDate(dto.getTaskDate());
        task.setTotalBags(dto.getTotalBags());
        Vehicle vehicle = suggestVehicle(area.getId(), dto.getTotalBags());
        task.setVehicleId(vehicle == null ? null : vehicle.getId());
        task.setStatus(KitchenTask.STATUS_PENDING);
        save(task);
        notifyAreaSanitation(area, task, vehicle);
        log.info("[Kitchen] 管理员发布清运任务：片区 {} 日期 {} 共 {} 袋",
                area.getAreaName(), task.getTaskDate(), task.getTotalBags());
        return task;
    }

    /** 后台：任务分页（日期/片区/状态过滤，areaName 联表填充供管理端展示） */
    public PageVO<KitchenTask> pageAdmin(LocalDate taskDate, Long areaId, Integer status,
                                         int pageNum, int pageSize) {
        Page<KitchenTask> result = page(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<KitchenTask>()
                .eq(taskDate != null, KitchenTask::getTaskDate, taskDate)
                .eq(areaId != null, KitchenTask::getAreaId, areaId)
                .eq(status != null, KitchenTask::getStatus, status)
                .orderByDesc(KitchenTask::getTaskDate));
        fillAreaName(result.getRecords());
        return PageVO.of(result);
    }

    /** 后台：改派车辆/环卫员（仅待执行任务） */
    @Transactional
    public KitchenTask reassign(Long taskId, Long vehicleId, Long staffId) {
        KitchenTask task = requireTask(taskId);
        if (task.getStatus() != KitchenTask.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "已归档任务不可改派");
        }
        if (vehicleId != null) {
            Vehicle vehicle = vehicleMapper.selectById(vehicleId);
            if (vehicle == null || vehicle.getStatus() != 1) {
                throw new BusinessException("车辆不存在或已停用");
            }
            task.setVehicleId(vehicleId);
        }
        if (staffId != null) {
            BizStaff staff = staffService.getById(staffId);
            if (staff == null || staff.getStaffType() != StaffTypeEnum.SANITATION.getCode()
                    || !Objects.equals(staff.getAreaId(), task.getAreaId())) {
                throw new BusinessException("仅可指派本片区环卫员");
            }
            task.setStaffId(staffId);
            messageSender.send(staff.getUserId(), MessageSender.TYPE_DISPATCHED,
                    "清运任务已指派", task.getTaskDate() + " 清运任务已指派给你，共 "
                            + task.getTotalBags() + " 袋");
        }
        updateById(task);
        return task;
    }

    // ==================== 公共 ====================

    /** 只读字段填充：任务关联片区名（员工端展示真实投放片区，替代前端硬编码） */
    private List<KitchenTask> fillAreaName(List<KitchenTask> tasks) {
        for (KitchenTask t : tasks) {
            BizArea area = t.getAreaId() == null ? null : areaService.getById(t.getAreaId());
            t.setAreaName(area == null ? null : area.getAreaName());
        }
        return tasks;
    }

    public KitchenTask requireTask(Long taskId) {
        KitchenTask task = getById(taskId);
        if (task == null) {
            throw new BusinessException("清运任务不存在");
        }
        return task;
    }

    /** 校验当前登录用户为在岗环卫员 */
    public BizStaff requireSanitation(Long staffUserId) {
        BizStaff staff = staffService.requireOnDutyByUserId(staffUserId);
        if (staff.getStaffType() != StaffTypeEnum.SANITATION.getCode()) {
            throw new BusinessException("仅环卫员可操作清运任务");
        }
        return staff;
    }

    /** 校验当前登录用户为在岗员工（回收/环卫/技术员均可查看清运任务列表） */
    public BizStaff requireStaff(Long staffUserId) {
        return staffService.requireOnDutyByUserId(staffUserId);
    }
}

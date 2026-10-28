package com.dianfei.business.recycle.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerService;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.service.ServiceSettleService;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.recycle.dto.RecycleFinishDTO;
import com.dianfei.business.recycle.dto.RecycleOrderCreateDTO;
import com.dianfei.business.recycle.entity.RecycleItem;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleItemMapper;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.recycle.vo.RecycleFinishVO;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 可回收物回收订单（B3）：下单→派单（DispatchService）→接单→上门→完成登记核算发分。
 * 状态机 0待接单 → 1已接单 → 2进行中 → 3完成 / 4取消 / 5异常，非法流转抛 2003（OrderStatusEnum）。
 * 完成登记按 biz_recycle_price 核算金额、经 CarbonLedgerService 发分入哈希链（11.1 闭环）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleOrderService extends ServiceImpl<RecycleOrderMapper, RecycleOrder> {

    private final RecyclePriceService priceService;
    private final CarbonLedgerService carbonLedgerService;
    private final ServiceSettleService serviceSettleService;
    private final MessageSender messageSender;
    private final AreaService areaService;
    private final StaffService staffService;
    private final RecycleItemMapper itemMapper;
    /** 跨模块只读查询 sys_user（客户姓名/电话，business → system 单向依赖） */
    private final SysUserMapper sysUserMapper;

    // ==================== 用户端 ====================

    /** 用户下单：AR 识别结果带入预约单，进入待派队列（11.1） */
    @Transactional
    public RecycleOrderVO create(Long userId, RecycleOrderCreateDTO dto) {
        if (dto == null || dto.getAppointTime() == null
                || dto.getCategory() == null || dto.getCategory().isBlank()
                || dto.getAddress() == null || dto.getAddress().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回收预约参数不完整");
        }
        if (dto.getEstWeight() != null && (dto.getEstWeight().signum() <= 0
                || dto.getEstWeight().compareTo(new BigDecimal("10000")) > 0)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预估重量必须在 0 到 10000kg 范围内");
        }
        if (dto.getQuantity() != null && (dto.getQuantity() <= 0 || dto.getQuantity() > 10000)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "件数必须在 1 到 10000 范围内");
        }
        if (dto.getAppointTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间不能早于当前时间");
        }
        // 片区解析：显式传入则校验启用；为空且带坐标 → 自动创建/匹配片区（精确到村）；两者皆无 → 明确报错
        Long areaId = resolveAreaId(dto.getAreaId(), dto.getLng(), dto.getLat());
        // 品类白名单归一化（electronics→E_WASTE、电子→E_WASTE 等；LLM 错拼 electronices 明确报错），
        // 再校验生效报价——避免小写 code/简称与报价表英文核算码不一致导致「未配置生效报价」误导
        String category = priceService.normalizeCategory(dto.getCategory());
        priceService.requireActive(category);
        // onsite02 服务费预检：非负 / 合法档位 / 余额充足（<=0=不使用积分支付）
        serviceSettleService.validate(userId, dto.getServicePoints(), ServiceSettleService.BIZ_RECYCLE);

        RecycleOrder order = new RecycleOrder();
        order.setOrderNo("RO" + IdUtil.getSnowflakeNextIdStr());
        order.setUserId(userId);
        order.setAreaId(areaId);
        order.setCategory(category);
        order.setEstWeight(dto.getEstWeight());
        order.setQuantity(dto.getQuantity());
        order.setAddress(dto.getAddress().trim());
        order.setLng(dto.getLng());
        order.setLat(dto.getLat());
        order.setAppointTime(dto.getAppointTime());
        order.setServicePoints(dto.getServicePoints() == null ? 0L : dto.getServicePoints());
        order.setSettleStatus(ServiceSettleService.SETTLE_NONE);
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        save(order);
        // 预约成功回执：消息中心可见（与接单/完成通知同一链路，失败不阻断下单）
        try {
            messageSender.send(userId, MessageSender.TYPE_ORDER_CREATED,
                    "回收预约成功",
                    "订单 " + order.getOrderNo() + " 已提交，预约上门 "
                            + dto.getAppointTime().toLocalDate() + " "
                            + String.format("%02d:%02d",
                                    dto.getAppointTime().getHour(), dto.getAppointTime().getMinute())
                            + "，等待回收员接单");
        } catch (Exception e) {
            log.warn("[Recycle] 预约成功站内信发送失败 orderNo={}", order.getOrderNo(), e);
        }
        return toVO(order, List.of());
    }

    /**
     * 维修不可修复件转回收：仍由回收域创建完整订单，避免联动入口直写冻结表。
     * 维修品类不是回收报价品类时统一归档为电子废弃物，完成登记仍必须经过正式报价核算。
     */
    @Transactional
    public Long createFromRepair(RepairOrder repairOrder) {
        Long areaId = repairOrder.getAreaId();
        try {
            areaService.requireEnabled(areaId);
        } catch (BusinessException e) {
            // 历史失效片区：带坐标则按坐标自动重建，兼容旧工单
            if (repairOrder.getLng() != null && repairOrder.getLat() != null) {
                areaId = areaService.createOrMatchAreaByLocation(
                        repairOrder.getLng().doubleValue(), repairOrder.getLat().doubleValue());
            } else {
                throw e;
            }
        }
        RecycleOrder order = new RecycleOrder();
        order.setOrderNo("RO" + IdUtil.getSnowflakeNextIdStr());
        order.setUserId(repairOrder.getUserId());
        order.setAreaId(areaId);
        order.setCategory("E_WASTE");
        order.setQuantity(1);
        order.setAddress(repairOrder.getAddress());
        order.setLng(repairOrder.getLng());
        order.setLat(repairOrder.getLat());
        order.setAppointTime(LocalDateTime.now().plusDays(1));
        order.setServicePoints(0L);
        order.setSettleStatus(ServiceSettleService.SETTLE_NONE);
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        order.setRepairRef(repairOrder.getId());
        save(order);
        return order.getId();
    }

    /** 片区解析：areaId 存在则校验启用（失效且带坐标 → 按坐标自动重建）；为空但有经纬度 → 自动创建/匹配（D0，精确到村）；否则明确报错 */
    private Long resolveAreaId(Long areaId, java.math.BigDecimal lng, java.math.BigDecimal lat) {
        if (areaId != null) {
            try {
                areaService.requireEnabled(areaId);
                return areaId;
            } catch (BusinessException e) {
                // 历史失效片区（旧种子被清理 / 雪花 ID 截断）：带坐标则按坐标自动创建/匹配，兼容旧地址簿数据
                if (lng != null && lat != null) {
                    return areaService.createOrMatchAreaByLocation(lng.doubleValue(), lat.doubleValue());
                }
                throw e;
            }
        }
        if (lng != null && lat != null) {
            return areaService.createOrMatchAreaByLocation(lng.doubleValue(), lat.doubleValue());
        }
        throw new BusinessException("片区不存在或已停用，请先定位或选择片区");
    }

    /** 用户端：我的回收单分页（转 VO 带接单员工姓名/电话） */
    public PageVO<RecycleOrderVO> pageUserOrders(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<RecycleOrder> wrapper = new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getUserId, userId)
                .eq(status != null, RecycleOrder::getStatus, status)
                .orderByDesc(RecycleOrder::getId);
        Page<RecycleOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageVO.of(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(o -> toVO(o, List.of())).toList());
    }

    /** 用户取消：仅 0待接单/1已接单 可取消，进行中及终态抛 2003 */
    @Transactional
    public void cancel(Long orderId, Long userId, String reason) {
        RecycleOrder order = requireOrder(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())
                && !OrderStatusEnum.ACCEPTED.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                    "当前状态[" + statusDesc(order.getStatus()) + "]不允许取消");
        }
        order.setStatus(OrderStatusEnum.CANCELLED.getCode());
        order.setCancelReason(reason);
        updateById(order);
        if (order.getStaffId() != null) {
            BizStaff staff = staffService.getById(order.getStaffId());
            if (staff != null) {
                messageSender.send(staff.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                        "回收单已取消", "回收单 " + order.getOrderNo() + " 已被用户取消");
            }
        }
    }

    /** 订单详情（含实收明细）；userId 非空时校验归属 */
    public RecycleOrderVO detail(Long orderId, Long userId) {
        RecycleOrder order = requireOrder(orderId);
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        List<RecycleItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<RecycleItem>().eq(RecycleItem::getOrderId, orderId));
        return toVO(order, items);
    }

    /** 员工查看订单（在岗员工均可查看全量详情，配合全量列表展示；接单/登记等操作仍限回收员） */
    public RecycleOrderVO detailForStaff(Long orderId, Long staffUserId) {
        requireStaff(staffUserId);
        RecycleOrder order = requireOrder(orderId);
        List<RecycleItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<RecycleItem>().eq(RecycleItem::getOrderId, orderId));
        return toVO(order, items);
    }

    /** 用户改期（G8）：仅 0待接单/1已接单 可改，直接改 appoint_time */
    @Transactional
    public void reschedule(Long orderId, Long userId, LocalDateTime appointTime) {
        RecycleOrder order = getOne(new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getId, orderId)
                .eq(RecycleOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException("回收订单不存在");
        }
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())
                && !OrderStatusEnum.ACCEPTED.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "仅待接单/已接单订单可改期");
        }
        order.setAppointTime(appointTime);
        updateById(order);
    }

    // ==================== 员工端（回收员） ====================

    /**
     * 今日订单（GET /api/staff/today-orders）：指派给我的 + 本片区未指派待接单（抢单）。
     * 片区隔离：Service 侧按员工绑定片区（biz_staff.area_id）过滤，不依赖 token 旧片区，避免双条件冲突查空。
     * 含已完成单（staff_id=我 的全部状态）——工作台“今日已完成”统计依赖；appoint_time 为空（尽快上门）也纳入。
     * 已完成单豁免 appoint_time 过滤：提前完成未来预约的单（如 13 日预约 11 日上门）也计入今日完成。
     * 出参转 VO 携带客户姓名/电话（员工端真实展示，替代前端硬编码「预约客户」）。
     */
    public List<RecycleOrderVO> todayOrders(Long staffUserId) {
        BizStaff staff = requireRecycler(staffUserId);
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<RecycleOrder> wrapper = new LambdaQueryWrapper<RecycleOrder>()
                .eq(RecycleOrder::getAreaId, staff.getAreaId())
                .and(w -> w.isNull(RecycleOrder::getAppointTime)
                        .or(ww -> ww.ge(RecycleOrder::getAppointTime, today.atStartOfDay())
                                .lt(RecycleOrder::getAppointTime, today.plusDays(1).atStartOfDay()))
                        .or(www -> www.eq(RecycleOrder::getStaffId, staff.getId())
                                .eq(RecycleOrder::getStatus, OrderStatusEnum.COMPLETED.getCode())))
                .and(w -> w.eq(RecycleOrder::getStaffId, staff.getId())
                        .or(ww -> ww.isNull(RecycleOrder::getStaffId)))
                .orderByAsc(RecycleOrder::getAppointTime);
        return list(wrapper).stream().map(o -> toVO(o, List.of())).toList();
    }

    /**
     * 员工端全量订单（GET /api/staff/orders/page）：跨片区、全状态分页展示（接单仍限本片区）。
     * 列表含所属片区名 areaName；status 为空查全部。
     */
    public PageVO<RecycleOrderVO> pageAllForStaff(Long staffUserId, Integer status, int pageNum, int pageSize) {
        requireStaff(staffUserId);
        LambdaQueryWrapper<RecycleOrder> wrapper = new LambdaQueryWrapper<RecycleOrder>()
                .eq(status != null, RecycleOrder::getStatus, status)
                .orderByDesc(RecycleOrder::getCreateTime);
        Page<RecycleOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageVO.of(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(o -> toVO(o, List.of())).toList());
    }

    /** 接单：0待接单 → 1已接单（限本片区；已被指派的单仅被指派人可接） */
    @Transactional
    public void accept(Long orderId, Long staffUserId) {
        BizStaff staff = requireRecycler(staffUserId);
        RecycleOrder order = requireOrder(orderId);
        requireStatus(order, OrderStatusEnum.PENDING);
        if (!Objects.equals(order.getAreaId(), staff.getAreaId())) {
            throw new BusinessException("仅可接本片区订单");
        }
        if (order.getStaffId() != null && !order.getStaffId().equals(staff.getId())) {
            throw new BusinessException("订单已指派给其他回收员");
        }
        order.setStaffId(staff.getId());
        order.setStatus(OrderStatusEnum.ACCEPTED.getCode());
        updateById(order);
        messageSender.send(order.getUserId(), MessageSender.TYPE_DISPATCHED,
                "回收员已接单", "回收员 " + staff.getStaffName() + " 将于预约时间上门回收");
    }

    /** 上门开始：1已接单 → 2进行中 */
    @Transactional
    public void start(Long orderId, Long staffUserId) {
        RecycleOrder order = requireOwnOrder(orderId, staffUserId);
        requireStatus(order, OrderStatusEnum.ACCEPTED);
        order.setStatus(OrderStatusEnum.IN_PROGRESS.getCode());
        updateById(order);
    }

    /**
     * 完成登记（PUT /api/staff/orders/{id}/finish）：2进行中 → 3完成。
     * 录实收明细+去向 → 按报价逐条核算金额 → 调 CarbonLedgerService 发分入哈希链（幂等由 bizKey 保证）
     */
    @Transactional
    public RecycleFinishVO finish(Long orderId, Long staffUserId, RecycleFinishDTO dto) {
        RecycleOrder order = requireOwnOrder(orderId, staffUserId);
        requireStatus(order, OrderStatusEnum.IN_PROGRESS);

        // 实收明细逐条核算（重复提交时覆盖）
        itemMapper.delete(new LambdaQueryWrapper<RecycleItem>().eq(RecycleItem::getOrderId, orderId));
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (RecycleFinishDTO.ItemDTO itemDto : dto.getItems()) {
            if (itemDto.getWeight().signum() <= 0) {
                throw new BusinessException("明细重量必须大于 0");
            }
            RecycleItem item = new RecycleItem();
            item.setOrderId(orderId);
            item.setCategory(itemDto.getCategory());
            item.setWeight(itemDto.getWeight());
            item.setAmount(priceService.settle(itemDto.getCategory(), itemDto.getWeight()));
            itemMapper.insert(item);
            totalWeight = totalWeight.add(item.getWeight());
            totalAmount = totalAmount.add(item.getAmount());
        }

        order.setWeight(totalWeight);
        order.setAmount(totalAmount);
        order.setTransferTo(dto.getTransferTo());
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setFinishTime(LocalDateTime.now());
        updateById(order);

        // onsite02 服务费结算：用户承诺积分转账给接单回收员（失败转人工，不阻断完成主链路）。
        // 必须先于发分：transfer 为 REQUIRES_NEW 独立事务，若发分已锁用户账户行（FOR UPDATE）
        // 再在子事务锁同一行将自死锁（Lock wait timeout）；先结算提交释放锁，再发分无冲突。
        BizStaff staff = order.getStaffId() == null ? null : staffService.getById(order.getStaffId());
        if (staff != null) {
            serviceSettleService.settle(order.getId(), ServiceSettleService.BIZ_RECYCLE, staff.getUserId());
            order = getById(orderId); // settle 已回写 settle_status，刷新本地引用供后续发分/VO 组装
        }

        // 碳积分入链：orderType+category 命中规则，按实收总重量计分（用户）
        CarbonLedgerResult result = carbonLedgerService.grant(CarbonGrantCmd.builder()
                .userId(order.getUserId())
                .orderType(OrderTypeEnum.RECYCLE)
                .orderId(order.getId())
                .category(order.getCategory())
                .weight(totalWeight)
                .build());

        // 工作人员获得与用户相同的回收积分（同规则同重量；bizKey 已含 userId，同单两笔流水不幂等冲突）
        CarbonLedgerResult staffResult = null;
        if (staff != null) {
            staffResult = carbonLedgerService.grant(CarbonGrantCmd.builder()
                    .userId(staff.getUserId())
                    .orderType(OrderTypeEnum.RECYCLE)
                    .orderId(order.getId())
                    .category(order.getCategory())
                    .weight(totalWeight)
                    .build());
            messageSender.send(staff.getUserId(), MessageSender.TYPE_POINTS_ARRIVED,
                    "回收积分到账", "回收单 " + order.getOrderNo() + " 您获得 +" + staffResult.getPoints()
                            + " 分（与用户相同），当前余额 " + staffResult.getBalanceAfter());
        }

        messageSender.send(order.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                "回收单已完成", "回收单 " + order.getOrderNo() + " 已完成，实收 " + totalWeight
                        + "kg，回款 " + totalAmount + " 元");
        messageSender.send(order.getUserId(), MessageSender.TYPE_POINTS_ARRIVED,
                "碳积分到账", "回收减排 +" + result.getPoints() + " 分，当前余额 " + result.getBalanceAfter());

        return RecycleFinishVO.builder()
                .orderId(String.valueOf(order.getId()))
                .orderNo(order.getOrderNo())
                .totalWeight(totalWeight)
                .amount(totalAmount)
                .points(result.getPoints())
                .balanceAfter(result.getBalanceAfter())
                .staffPoints(staffResult == null ? 0 : staffResult.getPoints())
                .staffBalanceAfter(staffResult == null ? 0 : staffResult.getBalanceAfter())
                .carbonReduced(result.getCarbonReduced())
                .carbonLogId(result.getLogId() == null ? null : String.valueOf(result.getLogId()))
                .settleStatus(order.getSettleStatus())
                .settlePoints(order.getServicePoints())
                .settleResult(ServiceSettleService.settleResultText(order.getServicePoints(), order.getSettleStatus()))
                .build();
    }

    /** 异常上报（POST /api/staff/exception）：1已接单/2进行中 → 5异常，后台按状态 5 列表复核 */
    @Transactional
    public void reportException(Long orderId, Long staffUserId, String reason) {
        RecycleOrder order = requireOwnOrder(orderId, staffUserId);
        if (!OrderStatusEnum.ACCEPTED.getCode().equals(order.getStatus())
                && !OrderStatusEnum.IN_PROGRESS.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                    "当前状态[" + statusDesc(order.getStatus()) + "]不允许异常上报");
        }
        order.setStatus(OrderStatusEnum.ABNORMAL.getCode());
        order.setExceptionReason(reason);
        updateById(order);
        messageSender.send(order.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                "回收单异常", "回收单 " + order.getOrderNo() + " 上报异常：" + reason + "，后台将尽快处理");
        // 通知后台：Track A 通知骨架就绪前以告警日志承载，后台经异常单列表（status=5）复核
        log.warn("[Recycle] 异常上报 order={} staffUserId={} reason={}", order.getOrderNo(), staffUserId, reason);
    }

    // ==================== 后台 ====================

    /** 后台：订单分页查询（状态/片区/用户可选过滤） */
    public PageVO<RecycleOrder> pageAdmin(Integer status, Long areaId, Long userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<RecycleOrder> wrapper = new LambdaQueryWrapper<RecycleOrder>()
                .eq(status != null, RecycleOrder::getStatus, status)
                .eq(areaId != null, RecycleOrder::getAreaId, areaId)
                .eq(userId != null, RecycleOrder::getUserId, userId)
                .orderByDesc(RecycleOrder::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 后台改派：0待接单/1已接单/5异常 可改派至本片区在岗回收员，重置为 0 待新员工接单 */
    @Transactional
    public void reassign(Long orderId, Long staffId) {
        RecycleOrder order = requireOrder(orderId);
        if (!OrderStatusEnum.PENDING.getCode().equals(order.getStatus())
                && !OrderStatusEnum.ACCEPTED.getCode().equals(order.getStatus())
                && !OrderStatusEnum.ABNORMAL.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                    "当前状态[" + statusDesc(order.getStatus()) + "]不允许改派");
        }
        BizStaff staff = staffService.getById(staffId);
        if (staff == null || staff.getAuditStatus() != BizStaff.AUDIT_APPROVED || staff.getStatus() != 1
                || staff.getStaffType() != StaffTypeEnum.RECYCLER.getCode()) {
            throw new BusinessException("改派目标不是在岗回收员");
        }
        if (!Objects.equals(staff.getAreaId(), order.getAreaId())) {
            throw new BusinessException("仅可改派给订单片区内的回收员");
        }
        order.setStaffId(staff.getId());
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        order.setExceptionReason(null);
        updateById(order);
        messageSender.send(staff.getUserId(), MessageSender.TYPE_DISPATCHED,
                "新回收单指派", "回收单 " + order.getOrderNo() + " 已指派给您，请及时接单");
    }

    // ==================== 公共 ====================

    public RecycleOrder requireOrder(Long orderId) {
        RecycleOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("回收订单不存在");
        }
        return order;
    }

    /** 校验订单归属当前回收员 */
    private RecycleOrder requireOwnOrder(Long orderId, Long staffUserId) {
        BizStaff staff = requireRecycler(staffUserId);
        RecycleOrder order = requireOrder(orderId);
        if (!staff.getId().equals(order.getStaffId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "订单未指派给当前回收员");
        }
        return order;
    }

    /** 校验当前登录用户为在岗回收员 */
    public BizStaff requireRecycler(Long staffUserId) {
        BizStaff staff = staffService.requireOnDutyByUserId(staffUserId);
        if (staff.getStaffType() != StaffTypeEnum.RECYCLER.getCode()) {
            throw new BusinessException("仅回收员可操作回收订单");
        }
        return staff;
    }

    /** 校验当前登录用户为在岗员工（回收/环卫/技术员均可查看回收订单列表与详情） */
    public BizStaff requireStaff(Long staffUserId) {
        return staffService.requireOnDutyByUserId(staffUserId);
    }

    public static void requireStatus(RecycleOrder order, OrderStatusEnum expected) {
        if (!expected.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                    "当前状态[" + statusDesc(order.getStatus()) + "]不允许该操作");
        }
    }

    public static String statusDesc(Integer status) {
        OrderStatusEnum e = OrderStatusEnum.of(status);
        return e == null ? String.valueOf(status) : e.getDesc();
    }

    /** 实体 → 出参 VO（含回收员姓名/电话与客户姓名/电话、实收明细） */
    public RecycleOrderVO toVO(RecycleOrder order, List<RecycleItem> items) {
        RecycleOrderVO vo = new RecycleOrderVO();
        vo.setId(String.valueOf(order.getId()));
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(String.valueOf(order.getUserId()));
        // 客户信息（sys_user 只读：昵称优先，未设昵称退回登录名；V39 起 sys_user 无电话列，客户电话见地址簿 contact_phone）
        SysUser customer = sysUserMapper.selectById(order.getUserId());
        if (customer != null) {
            vo.setCustomerName(customer.getNickname() == null || customer.getNickname().isBlank()
                    ? customer.getUsername() : customer.getNickname());
        }
        if (order.getStaffId() != null) {
            vo.setStaffId(String.valueOf(order.getStaffId()));
            BizStaff staff = staffService.getById(order.getStaffId());
            vo.setStaffName(staff == null ? null : staff.getStaffName());
            vo.setStaffPhone(staff == null ? null : staff.getPhone());
        }
        vo.setAreaId(String.valueOf(order.getAreaId()));
        BizArea area = order.getAreaId() == null ? null : areaService.getById(order.getAreaId());
        vo.setAreaName(area == null ? null : area.getAreaName());
        vo.setCategory(order.getCategory());
        vo.setEstWeight(order.getEstWeight());
        vo.setWeight(order.getWeight());
        vo.setQuantity(order.getQuantity());
        vo.setAddress(order.getAddress());
        vo.setLng(order.getLng());
        vo.setLat(order.getLat());
        vo.setAppointTime(order.getAppointTime());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(statusDesc(order.getStatus()));
        vo.setAmount(order.getAmount());
        vo.setTransferTo(order.getTransferTo());
        vo.setRepairRef(order.getRepairRef() == null ? null : String.valueOf(order.getRepairRef()));
        vo.setCancelReason(order.getCancelReason());
        vo.setExceptionReason(order.getExceptionReason());
        vo.setServicePoints(order.getServicePoints());
        vo.setSettleStatus(order.getSettleStatus());
        vo.setSettlePoints(order.getServicePoints());
        vo.setSettleResult(ServiceSettleService.settleResultText(order.getServicePoints(), order.getSettleStatus()));
        vo.setFinishTime(order.getFinishTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(items.stream().map(i -> {
            RecycleOrderVO.ItemVO iv = new RecycleOrderVO.ItemVO();
            iv.setCategory(i.getCategory());
            iv.setWeight(i.getWeight());
            iv.setAmount(i.getAmount());
            return iv;
        }).toList());
        return vo;
    }
}

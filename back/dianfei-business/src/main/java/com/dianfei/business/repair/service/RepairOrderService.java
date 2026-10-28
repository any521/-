package com.dianfei.business.repair.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.carbon.CarbonLedgerService;
import com.dianfei.business.carbon.dto.CarbonGrantCmd;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.carbon.service.ServiceSettleService;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.repair.diagnose.RepairDiagnoseClient;
import com.dianfei.business.repair.diagnose.RepairDiagnoseContract;
import com.dianfei.business.repair.dto.RepairOrderCreateDTO;
import com.dianfei.business.repair.dto.RepairReportDTO;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.entity.RepairPart;
import com.dianfei.business.repair.mapper.RepairOrderMapper;
import com.dianfei.business.repair.mapper.RepairPartMapper;
import com.dianfei.business.repair.vo.RepairOrderVO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.enums.RepairStatusEnum;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 电路维修工单（C1）：下单(F2 预诊)→接单→检修→报告→完成发分 / 判定不可修复待转回收。
 * 状态机见 RepairStatusEnum；转回收联动（C2）见 RepairLinkageService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepairOrderService extends ServiceImpl<RepairOrderMapper, RepairOrder> {

    private final RepairDiagnoseClient diagnoseClient;
    private final CarbonLedgerService carbonLedgerService;
    private final ServiceSettleService serviceSettleService;
    private final MessageSender messageSender;
    private final AreaService areaService;
    private final StaffService staffService;
    private final RepairPartMapper repairPartMapper;
    /** 跨模块只读查询 sys_user（客户姓名/电话，business → system 单向依赖） */
    private final SysUserMapper sysUserMapper;

    /** 用户下单：F2 预诊建议随单落库，AI 不可用降级为自由备注（D15） */
    @Transactional
    public RepairOrderVO create(Long userId, RepairOrderCreateDTO dto) {
        if (!dto.getAppointStart().isBefore(dto.getAppointEnd())) {
            throw new BusinessException("上门时段不合法");
        }
        // 片区解析：显式传入则校验启用；为空且带坐标 → 自动创建/匹配片区（精确到村）；两者皆无 → 明确报错
        Long areaId = resolveAreaId(dto.getAreaId(), dto.getLng(), dto.getLat());
        // onsite02 服务费预检：非负 / 合法档位 / 余额充足（<=0=不使用积分支付）
        serviceSettleService.validate(userId, dto.getServicePoints(), ServiceSettleService.BIZ_REPAIR);

        RepairOrder order = new RepairOrder();
        order.setOrderNo("RP" + IdUtil.getSnowflakeNextIdStr());
        order.setUserId(userId);
        order.setAreaId(areaId);
        order.setFaultType(dto.getFaultType());
        order.setFaultDesc(dto.getFaultDesc());
        order.setAddress(dto.getAddress());
        order.setLng(dto.getLng());
        order.setLat(dto.getLat());
        order.setAppointStart(dto.getAppointStart());
        order.setAppointEnd(dto.getAppointEnd());
        order.setOldForNew(dto.getOldForNew() == null ? 0 : dto.getOldForNew());
        order.setGasDelivery(dto.getGasDelivery() == null ? 0 : dto.getGasDelivery());
        order.setServicePoints(dto.getServicePoints() == null ? 0L : dto.getServicePoints());
        order.setSettleStatus(ServiceSettleService.SETTLE_NONE);
        order.setRepairable(0);
        order.setStatus(RepairStatusEnum.PENDING.getCode());
        order.setDiagnoseJson(tryDiagnose(dto));
        save(order);
        // 预约成功回执：消息中心可见（失败不阻断下单主链路）
        try {
            messageSender.send(userId, MessageSender.TYPE_ORDER_CREATED,
                    "维修预约成功",
                    "订单 " + order.getOrderNo() + " 已提交，预约上门 "
                            + dto.getAppointStart() + " 至 " + dto.getAppointEnd()
                            + "，等待维修员接单");
        } catch (Exception e) {
            log.warn("[Repair] 预约成功站内信发送失败 orderNo={}", order.getOrderNo(), e);
        }
        return toVO(order, List.of());
    }

    /** F2 预诊：任何异常降级为 null，不阻塞下单主链路 */
    private String tryDiagnose(RepairOrderCreateDTO dto) {
        try {
            RepairDiagnoseContract.Response resp = diagnoseClient.diagnose(RepairDiagnoseContract.Request.builder()
                    .faultType(dto.getFaultType())
                    .faultDesc(dto.getFaultDesc())
                    .build());
            return resp == null ? null : JSONUtil.toJsonStr(resp);
        } catch (Exception e) {
            log.warn("[Repair] F2 预诊不可用，降级为自由备注: {}", e.getMessage());
            return null;
        }
    }

    /** 用户端：我的维修单分页（转 VO 带客户/员工信息） */
    public PageVO<RepairOrderVO> pageUserOrders(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getUserId, userId)
                .eq(status != null, RepairOrder::getStatus, status)
                .orderByDesc(RepairOrder::getId);
        Page<RepairOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageVO.of(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(o -> toVO(o, List.of())).toList());
    }

    /** 后台：维修工单分页（status/areaId/repairable/orderNo 可选过滤） */
    public PageVO<RepairOrder> pageAdmin(Integer status, Long areaId, Integer repairable, String orderNo,
                                         int pageNum, int pageSize) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<RepairOrder>()
                .eq(status != null, RepairOrder::getStatus, status)
                .eq(areaId != null, RepairOrder::getAreaId, areaId)
                .eq(repairable != null, RepairOrder::getRepairable, repairable)
                .eq(orderNo != null && !orderNo.isBlank(), RepairOrder::getOrderNo, orderNo)
                .orderByDesc(RepairOrder::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 片区解析：areaId 存在则校验启用（失效且带坐标 → 按坐标自动重建）；为空但有经纬度 → 自动创建/匹配（D0，精确到村）；否则明确报错 */
    private Long resolveAreaId(Long areaId, BigDecimal lng, BigDecimal lat) {
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

    /** 用户取消：仅 0待接单/1已接单 可取消 */
    @Transactional
    public void cancel(Long orderId, Long userId, String reason) {
        RepairOrder order = requireOrder(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (order.getStatus() != RepairStatusEnum.PENDING.getCode()
                && order.getStatus() != RepairStatusEnum.ACCEPTED.getCode()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "检修已开始，无法取消");
        }
        order.setStatus(RepairStatusEnum.CANCELLED.getCode());
        order.setCancelReason(reason);
        updateById(order);
        if (order.getStaffId() != null) {
            BizStaff staff = staffService.getById(order.getStaffId());
            if (staff != null) {
                messageSender.send(staff.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                        "维修单已取消", "工单 " + order.getOrderNo() + " 已被用户取消");
            }
        }
    }

    /** 员工端：本片区待接单列表（在岗员工可浏览，接单仍限技术员；转 VO 带客户姓名/电话） */
    public PageVO<RepairOrderVO> pagePending(Long staffUserId, int pageNum, int pageSize) {
        BizStaff staff = requireStaff(staffUserId);
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getAreaId, staff.getAreaId())
                .eq(RepairOrder::getStatus, RepairStatusEnum.PENDING.getCode())
                .orderByAsc(RepairOrder::getAppointStart);
        Page<RepairOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageVO.of(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(o -> toVO(o, List.of())).toList());
    }

    /** 员工端：我的工单列表（在岗员工可查看本人指派单，转 VO 带客户姓名/电话） */
    public PageVO<RepairOrderVO> pageStaffOrders(Long staffUserId, Integer status, int pageNum, int pageSize) {
        BizStaff staff = requireStaff(staffUserId);
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStaffId, staff.getId())
                .eq(status != null, RepairOrder::getStatus, status)
                .orderByAsc(RepairOrder::getAppointStart);
        Page<RepairOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageVO.of(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(o -> toVO(o, List.of())).toList());
    }

    /**
     * 员工端全量工单（GET /api/staff/repair/orders/all）：在岗员工均可查看，跨片区、全状态分页展示（接单仍限本片区技术员）。
     * 列表含所属片区名 areaName；status 为空查全部。
     */
    public PageVO<RepairOrderVO> pageAllForStaff(Long staffUserId, Integer status, int pageNum, int pageSize) {
        requireStaff(staffUserId);
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<RepairOrder>()
                .eq(status != null, RepairOrder::getStatus, status)
                .orderByDesc(RepairOrder::getCreateTime);
        Page<RepairOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        return PageVO.of(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(o -> toVO(o, List.of())).toList());
    }

    /** 技术员接单：0待接单 → 1已接单（限本片区） */
    @Transactional
    public void accept(Long orderId, Long staffUserId) {
        BizStaff staff = requireTechnician(staffUserId);
        RepairOrder order = requireOrder(orderId);
        requireStatus(order, RepairStatusEnum.PENDING);
        if (!Objects.equals(order.getAreaId(), staff.getAreaId())) {
            throw new BusinessException("仅可接本片区工单");
        }
        order.setStaffId(staff.getId());
        order.setStatus(RepairStatusEnum.ACCEPTED.getCode());
        updateById(order);
        messageSender.send(order.getUserId(), MessageSender.TYPE_DISPATCHED,
                "维修师傅已接单", "技术员 " + staff.getStaffName() + " 将于预约时段上门检修");
    }

    /** 上门开始检修：1已接单 → 2检修中 */
    @Transactional
    public void startCheck(Long orderId, Long staffUserId) {
        RepairOrder order = requireOwnOrder(orderId, staffUserId);
        requireStatus(order, RepairStatusEnum.ACCEPTED);
        order.setStatus(RepairStatusEnum.CHECKING.getCode());
        updateById(order);
    }

    /**
     * 提交检修报告（可修复性判定 + 配件 + 科普留痕）：
     * 判定可修复 → 3已完成，发放"维修替代换新"积分；
     * 判定不可修复 → 保持 2检修中，等待转回收联动（C2）
     */
    @Transactional
    public void submitReport(Long orderId, Long staffUserId, RepairReportDTO dto) {
        RepairOrder order = requireOwnOrder(orderId, staffUserId);
        requireStatus(order, RepairStatusEnum.CHECKING);
        if (dto.getRepairable() != 1 && dto.getRepairable() != 2) {
            throw new BusinessException("可修复性判定仅支持 1可修复 / 2不可修复");
        }
        // 配件登记（重复提交报告时覆盖）
        repairPartMapper.delete(new LambdaQueryWrapper<RepairPart>().eq(RepairPart::getOrderId, orderId));
        int partQty = 0;
        if (dto.getParts() != null) {
            for (RepairReportDTO.PartItem item : dto.getParts()) {
                RepairPart part = new RepairPart();
                part.setOrderId(orderId);
                part.setPartName(item.getPartName());
                part.setPartModel(item.getPartModel());
                part.setQuantity(item.getQuantity() == null ? 1 : item.getQuantity());
                part.setPrice(item.getPrice());
                repairPartMapper.insert(part);
                partQty += part.getQuantity();
            }
        }
        order.setRepairable(dto.getRepairable());
        order.setReportJson(JSONUtil.toJsonStr(dto));
        order.setScienceNote(dto.getScienceNote());

        if (dto.getRepairable() == 1) {
            order.setStatus(RepairStatusEnum.COMPLETED.getCode());
            order.setFinishTime(LocalDateTime.now());
            updateById(order);
            // onsite02 服务费结算：用户承诺积分转账给接单技术员（失败转人工，不阻断完成主链路）。
            // 必须先于发分：transfer 为 REQUIRES_NEW 独立事务，若发分已锁用户账户行（FOR UPDATE）
            // 再在子事务锁同一行将自死锁（Lock wait timeout）；先结算提交释放锁，再发分无冲突。
            BizStaff staff = staffService.getById(order.getStaffId());
            if (staff != null) {
                serviceSettleService.settle(order.getId(), ServiceSettleService.BIZ_REPAIR, staff.getUserId());
            }
            grantRepairPoints(order, Math.max(partQty, 1));
        } else {
            // 不可修复：老旧插排/故障小家电强制走转回收链路（C2）
            updateById(order);
            messageSender.send(order.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                    "检修判定：不可修复", "工单 " + order.getOrderNo() + " 判定不可修复，将为您转入回收流程");
        }
    }

    /** 维修完成发放"维修替代换新"积分（幂等由碳账本 bizKey 保证） */
    private void grantRepairPoints(RepairOrder order, int quantity) {
        CarbonLedgerResult result = carbonLedgerService.grant(CarbonGrantCmd.builder()
                .userId(order.getUserId())
                .orderType(OrderTypeEnum.REPAIR)
                .orderId(order.getId())
                .category("REPAIR_REPLACE")
                .quantity(quantity)
                .build());
        messageSender.send(order.getUserId(), MessageSender.TYPE_POINTS_ARRIVED,
                "维修积分到账", "维修替代换新 +" + result.getPoints() + " 分，当前余额 " + result.getBalanceAfter());
    }

    /** 工单详情（含配件与预诊解析）；userId 非空时校验归属 */
    public RepairOrderVO detail(Long orderId, Long userId) {
        RepairOrder order = requireOrder(orderId);
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        List<RepairPart> parts = repairPartMapper.selectList(
                new LambdaQueryWrapper<RepairPart>().eq(RepairPart::getOrderId, orderId));
        return toVO(order, parts);
    }

    /** 员工查看工单（在岗员工均可查看全量详情，配合全量列表展示；接单/检修/报告等操作仍限技术员） */
    public RepairOrderVO detailForStaff(Long orderId, Long staffUserId) {
        requireStaff(staffUserId);
        RepairOrder order = requireOrder(orderId);
        List<RepairPart> parts = repairPartMapper.selectList(
                new LambdaQueryWrapper<RepairPart>().eq(RepairPart::getOrderId, orderId));
        return toVO(order, parts);
    }

    public RepairOrder requireOrder(Long orderId) {
        RepairOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("维修工单不存在");
        }
        return order;
    }

    /** 校验工单归属当前技术员 */
    private RepairOrder requireOwnOrder(Long orderId, Long staffUserId) {
        BizStaff staff = requireTechnician(staffUserId);
        RepairOrder order = requireOrder(orderId);
        if (!staff.getId().equals(order.getStaffId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "工单未指派给当前技术员");
        }
        return order;
    }

    /** 校验当前登录用户为在岗维修技术员（接单/检修/报告等操作类复用） */
    public BizStaff requireTechnician(Long staffUserId) {
        BizStaff staff = staffService.requireOnDutyByUserId(staffUserId);
        if (staff.getStaffType() != StaffTypeEnum.TECHNICIAN.getCode()) {
            throw new BusinessException("仅维修技术员可操作维修工单");
        }
        return staff;
    }

    /** 校验当前登录用户为在岗员工（回收/环卫/技术员均可查看维修工单列表与详情） */
    public BizStaff requireStaff(Long staffUserId) {
        return staffService.requireOnDutyByUserId(staffUserId);
    }

    public static void requireStatus(RepairOrder order, RepairStatusEnum expected) {
        if (!expected.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                    "当前状态[" + statusDesc(order.getStatus()) + "]不允许该操作");
        }
    }

    public static String statusDesc(Integer status) {
        RepairStatusEnum e = RepairStatusEnum.of(status);
        return e == null ? String.valueOf(status) : e.getDesc();
    }

    /** 实体 → 出参 VO（解析预诊 JSON + 配件明细 + 客户/员工姓名电话） */
    private RepairOrderVO toVO(RepairOrder order, List<RepairPart> parts) {
        RepairOrderVO vo = new RepairOrderVO();
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
        vo.setFaultType(order.getFaultType());
        vo.setFaultDesc(order.getFaultDesc());
        vo.setAddress(order.getAddress());
        vo.setAppointStart(order.getAppointStart());
        vo.setAppointEnd(order.getAppointEnd());
        vo.setOldForNew(order.getOldForNew());
        vo.setGasDelivery(order.getGasDelivery());
        if (order.getDiagnoseJson() != null && !order.getDiagnoseJson().isBlank()) {
            vo.setDiagnose(JSONUtil.toBean(order.getDiagnoseJson(), RepairDiagnoseContract.Response.class));
        }
        vo.setRepairable(order.getRepairable());
        vo.setScienceNote(order.getScienceNote());
        vo.setRecycleRef(order.getRecycleRef() == null ? null : String.valueOf(order.getRecycleRef()));
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(statusDesc(order.getStatus()));
        vo.setCancelReason(order.getCancelReason());
        vo.setServicePoints(order.getServicePoints());
        vo.setSettleStatus(order.getSettleStatus());
        vo.setSettlePoints(order.getServicePoints());
        vo.setSettleResult(ServiceSettleService.settleResultText(order.getServicePoints(), order.getSettleStatus()));
        vo.setFinishTime(order.getFinishTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setParts(parts.stream().map(p -> {
            RepairOrderVO.PartVO pv = new RepairOrderVO.PartVO();
            pv.setPartName(p.getPartName());
            pv.setPartModel(p.getPartModel());
            pv.setQuantity(p.getQuantity());
            pv.setPrice(p.getPrice());
            return pv;
        }).toList());
        return vo;
    }
}

package com.dianfei.business.recycle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.carbon.dto.CarbonRuleSaveDTO;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.service.CarbonRuleService;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.message.service.MessageService;
import com.dianfei.business.recycle.dispatch.RecycleDispatchService;
import com.dianfei.business.recycle.dto.RecycleFinishDTO;
import com.dianfei.business.recycle.dto.RecycleOrderCreateDTO;
import com.dianfei.business.recycle.dto.RecyclePriceSaveDTO;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.service.RecycleOrderService;
import com.dianfei.business.recycle.service.RecyclePriceService;
import com.dianfei.business.recycle.vo.RecycleFinishVO;
import com.dianfei.business.recycle.vo.RecycleOrderVO;
import com.dianfei.business.staff.dto.AreaSaveDTO;
import com.dianfei.business.staff.dto.StaffAuditDTO;
import com.dianfei.business.staff.dto.StaffCreateDTO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.staff.service.StaffService;
import com.dianfei.common.enums.OrderStatusEnum;
import com.dianfei.common.enums.StaffTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B3 回收闭环验收单测（H2，对齐 trackB 验收标准）：
 * 下单→派单→接单→完成登记→金额与积分到账（rule_code/carbon_reduced/prev_hash/curr_hash 链接正确）/
 * 零代码新增品类（报价+规则纯配置即可下单获分）/ 片区隔离（片区 2 员工看不到片区 1 订单）/ 状态机 2003
 */
@SpringBootTest
class RecycleClosedLoopTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(80_000L);
    private static final AtomicLong CAT_SEQ = new AtomicLong(0L);

    @Autowired
    private RecycleOrderService orderService;
    @Autowired
    private RecyclePriceService priceService;
    @Autowired
    private RecycleDispatchService dispatchService;
    @Autowired
    private CarbonRuleService ruleService;
    @Autowired
    private CarbonAccountMapper accountMapper;
    @Autowired
    private CarbonLogMapper logMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private AreaService areaService;
    @Autowired
    private StaffService staffService;

    @Test
    void closedLoopWithHashChainAndZeroCodeCategory() {
        BizArea area = area("回收片区");
        BizStaff recycler = onDutyRecycler(area.getId());
        Long userId = USER_SEQ.incrementAndGet();
        // 零代码新增品类：仅配置报价 2 元/kg + 规则 10 分/kg、0.5 kgCO2/kg，即可下单获分（09.3）
        String category = newCategory(new BigDecimal("2.00"), new BigDecimal("10"), new BigDecimal("0.5"));

        // 下单 → 派单（明日 07:00 定时同入口，幂等只处理未指派单）→ 派单成功站内信
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        RecycleOrderVO created = orderService.create(userId, createDTO(area.getId(), category, tomorrow.atTime(10, 0)));
        Long orderId = Long.valueOf(created.getId());
        assertThat(dispatchService.dispatchDate(tomorrow)).isEqualTo(1);
        assertThat(orderService.getById(orderId).getStaffId()).isEqualTo(recycler.getId());
        assertThat(messageService.pageMine(recycler.getUserId(), MessageSender.TYPE_DISPATCHED, null, 1, 10)
                .getTotal()).isEqualTo(1);
        assertThat(dispatchService.dispatchDate(tomorrow)).isZero(); // 幂等重跑不重复指派

        // 接单→上门→完成登记：3.5+1.5 kg → 金额 10.00 元、50 分、减排 2.5 kgCO2
        orderService.accept(orderId, recycler.getUserId());
        orderService.start(orderId, recycler.getUserId());
        RecycleFinishVO finish = orderService.finish(orderId, recycler.getUserId(),
                finishDTO(category, "3.5", "1.5"));
        assertThat(finish.getAmount()).isEqualByComparingTo("10.00");
        assertThat(finish.getPoints()).isEqualTo(50L);
        assertThat(finish.getCarbonReduced()).isEqualByComparingTo("2.5");
        assertThat(orderService.getById(orderId).getStatus()).isEqualTo(OrderStatusEnum.COMPLETED.getCode());

        // 流水字段与哈希链：rule_code 命中、carbon_reduced 正确、第二笔 prev_hash 衔接第一笔 curr_hash
        RecycleOrderVO second = orderService.create(userId, createDTO(area.getId(), category, tomorrow.atTime(11, 0)));
        Long secondId = Long.valueOf(second.getId());
        orderService.accept(secondId, recycler.getUserId());
        orderService.start(secondId, recycler.getUserId());
        orderService.finish(secondId, recycler.getUserId(), finishDTO(category, "2.0"));

        CarbonAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<CarbonAccount>().eq(CarbonAccount::getUserId, userId));
        assertThat(account.getPointsBalance()).isEqualTo(70L);
        List<CarbonLog> logs = logMapper.selectList(new LambdaQueryWrapper<CarbonLog>()
                .eq(CarbonLog::getAccountId, account.getId()).orderByAsc(CarbonLog::getId));
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getRuleCode()).isEqualTo("RECYCLE_" + category);
        assertThat(logs.get(0).getCarbonReduced()).isEqualByComparingTo("2.5");
        assertThat(logs.get(0).getCurrHash()).isNotBlank();
        assertThat(logs.get(1).getPrevHash()).isEqualTo(logs.get(0).getCurrHash());

        // B5 消息触发：订单完成 + 积分到账均落库
        assertThat(messageService.pageMine(userId, MessageSender.TYPE_ORDER_FINISHED, null, 1, 10)
                .getTotal()).isEqualTo(2);
        assertThat(messageService.pageMine(userId, MessageSender.TYPE_POINTS_ARRIVED, null, 1, 10)
                .getTotal()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void areaIsolationBetweenStaff() {
        BizArea area1 = area("片区一");
        BizArea area2 = area("片区二");
        BizStaff staff1 = onDutyRecycler(area1.getId());
        BizStaff staff2 = onDutyRecycler(area2.getId());
        String category = newCategory(new BigDecimal("1.00"), new BigDecimal("5"), new BigDecimal("0.2"));

        // 今日单落在片区 1：片区 1 员工可见，片区 2 员工不可见（@DataScope + Service 双保险）
        RecycleOrderVO created = orderService.create(USER_SEQ.incrementAndGet(),
                createDTO(area1.getId(), category, LocalDateTime.now().plusMinutes(30)));
        Long orderId = Long.valueOf(created.getId());
        assertThat(orderService.todayOrders(staff1.getUserId()))
                .extracting(RecycleOrderVO::getId).contains(created.getId());
        assertThat(orderService.todayOrders(staff2.getUserId()))
                .extracting(RecycleOrderVO::getId).doesNotContain(created.getId());

        // 跨片区接单被拒
        assertThatThrownBy(() -> orderService.accept(orderId, staff2.getUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅可接本片区订单");
    }

    @Test
    void illegalTransitionReturns2003() {
        BizArea area = area("状态机片区");
        BizStaff recycler = onDutyRecycler(area.getId());
        String category = newCategory(new BigDecimal("1.00"), new BigDecimal("5"), new BigDecimal("0.2"));
        Long userId = USER_SEQ.incrementAndGet();

        // 已接单未开始就完成登记 → 2003
        RecycleOrderVO created = orderService.create(userId,
                createDTO(area.getId(), category, LocalDateTime.now().plusHours(1)));
        Long orderId = Long.valueOf(created.getId());
        orderService.accept(orderId, recycler.getUserId());
        assertThatThrownBy(() -> orderService.finish(orderId, recycler.getUserId(), finishDTO(category, "1.0")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);

        // 已取消再接单 → 2003
        RecycleOrderVO another = orderService.create(userId,
                createDTO(area.getId(), category, LocalDateTime.now().plusHours(1)));
        Long anotherId = Long.valueOf(another.getId());
        orderService.cancel(anotherId, userId, "计划有变");
        assertThatThrownBy(() -> orderService.accept(anotherId, recycler.getUserId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getResultCode())
                .isEqualTo(ResultCode.ORDER_STATUS_NOT_ALLOWED);
    }

    // ---------- 测试辅助 ----------

    /** 零代码新品类：报价 + 碳规则纯配置（品类唯一化避免用例间串扰） */
    private String newCategory(BigDecimal pricePerKg, BigDecimal pointsPerKg, BigDecimal carbonPerKg) {
        String category = "NEW_CAT_" + CAT_SEQ.incrementAndGet() + "_" + USER_SEQ.incrementAndGet();
        RecyclePriceSaveDTO price = new RecyclePriceSaveDTO();
        price.setCategory(category);
        price.setPricePerKg(pricePerKg);
        price.setStatus(1);
        priceService.saveByCategory(price);

        CarbonRuleSaveDTO rule = new CarbonRuleSaveDTO();
        rule.setRuleCode("RECYCLE_" + category);
        rule.setOrderType("RECYCLE");
        rule.setCategory(category);
        rule.setPointsPerKg(pointsPerKg);
        rule.setCarbonPerKg(carbonPerKg);
        rule.setStatus(1);
        ruleService.save(rule);
        return category;
    }

    private BizArea area(String name) {
        AreaSaveDTO dto = new AreaSaveDTO();
        dto.setAreaName(name + "-" + USER_SEQ.incrementAndGet());
        dto.setCleanThreshold(100);
        dto.setStatus(1);
        return areaService.saveArea(dto);
    }

    private BizStaff onDutyRecycler(Long areaId) {
        long staffUserId = USER_SEQ.incrementAndGet();
        StaffCreateDTO dto = new StaffCreateDTO();
        dto.setUserId(staffUserId);
        dto.setStaffName("回收员" + staffUserId);
        dto.setPhone("13800000000");
        dto.setStaffType(StaffTypeEnum.RECYCLER.getCode());
        dto.setAreaId(areaId);
        BizStaff staff = staffService.create(dto);
        StaffAuditDTO audit = new StaffAuditDTO();
        audit.setApproved(true);
        staffService.audit(staff.getId(), audit);
        return staffService.getById(staff.getId());
    }

    private static RecycleOrderCreateDTO createDTO(Long areaId, String category, LocalDateTime appointTime) {
        RecycleOrderCreateDTO dto = new RecycleOrderCreateDTO();
        dto.setAreaId(areaId);
        dto.setCategory(category);
        dto.setEstWeight(new BigDecimal("5.0"));
        dto.setAddress("绿园小区 1-1-101");
        dto.setAppointTime(appointTime);
        return dto;
    }

    private static RecycleFinishDTO finishDTO(String category, String... weights) {
        RecycleFinishDTO dto = new RecycleFinishDTO();
        dto.setTransferTo("市再生资源分拣中心");
        dto.setItems(java.util.Arrays.stream(weights).map(w -> {
            RecycleFinishDTO.ItemDTO item = new RecycleFinishDTO.ItemDTO();
            item.setCategory(category);
            item.setWeight(new BigDecimal(w));
            return item;
        }).toList());
        return dto;
    }
}

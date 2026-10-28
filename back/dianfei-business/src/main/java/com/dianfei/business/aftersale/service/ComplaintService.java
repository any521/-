package com.dianfei.business.aftersale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.aftersale.dto.ComplaintCreateDTO;
import com.dianfei.business.aftersale.dto.ComplaintHandleDTO;
import com.dianfei.business.aftersale.entity.Complaint;
import com.dianfei.business.aftersale.mapper.ComplaintMapper;
import com.dianfei.business.carbon.CarbonLedgerService;
import com.dianfei.business.carbon.dto.CarbonAdjustCmd;
import com.dianfei.business.message.MessageSender;
import com.dianfei.common.enums.OrderTypeEnum;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单售后（C4）：用户提交投诉/退款/追责/补发/仲裁，后台处置。
 * 补发/扣分走账本 adjust（bizKey=orderType:complaintId:REISSUE|DEDUCT，与来源单流水隔离，幂等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintService extends ServiceImpl<ComplaintMapper, Complaint> {

    private final CarbonLedgerService carbonLedgerService;
    private final MessageSender messageSender;

    /** 用户提交售后 */
    @Transactional
    public Complaint submit(Long userId, ComplaintCreateDTO dto) {
        OrderTypeEnum orderType = OrderTypeEnum.of(dto.getOrderType());
        if (orderType == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "orderType 不合法");
        }
        Complaint complaint = new Complaint();
        complaint.setOrderType(orderType.getCode());
        complaint.setOrderId(dto.getOrderId());
        complaint.setUserId(userId);
        complaint.setType(dto.getType());
        complaint.setContent(dto.getContent());
        complaint.setStatus(Complaint.STATUS_PENDING);
        save(complaint);
        return complaint;
    }

    /** 用户端：我的售后单分页 */
    public PageVO<Complaint> pageUser(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getUserId, userId)
                .eq(status != null, Complaint::getStatus, status)
                .orderByDesc(Complaint::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 后台：售后单分页（状态/类型/来源单类型过滤） */
    public PageVO<Complaint> pageAdmin(Integer status, Integer type, String orderType, int pageNum, int pageSize) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<Complaint>()
                .eq(status != null, Complaint::getStatus, status)
                .eq(type != null, Complaint::getType, type)
                .eq(orderType != null && !orderType.isBlank(), Complaint::getOrderType, orderType)
                .orderByAsc(Complaint::getStatus).orderByDesc(Complaint::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 后台处置（仅待处理单）：受理可选补发积分（REISSUE）与追责扣分（DEDUCT），
     * 账本流水与状态回写同事务，失败整体回滚
     */
    @Transactional
    public Complaint handle(Long complaintId, Long handlerId, ComplaintHandleDTO dto) {
        Complaint complaint = requireComplaint(complaintId);
        if (complaint.getStatus() != Complaint.STATUS_PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "该售后单已处理");
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        if (approved) {
            OrderTypeEnum orderType = OrderTypeEnum.of(complaint.getOrderType());
            if (dto.getCompensatePoints() != null) {
                carbonLedgerService.adjust(CarbonAdjustCmd.builder()
                        .userId(complaint.getUserId())
                        .orderType(orderType)
                        .orderId(complaint.getId())
                        .points(dto.getCompensatePoints())
                        .reason("售后补发：" + defaultResult(dto))
                        .build());
            }
            if (dto.getDeductPoints() != null) {
                carbonLedgerService.adjust(CarbonAdjustCmd.builder()
                        .userId(complaint.getUserId())
                        .orderType(orderType)
                        .orderId(complaint.getId())
                        .points(-dto.getDeductPoints())
                        .reason("售后追责扣除：" + defaultResult(dto))
                        .build());
            }
        }
        complaint.setStatus(approved ? Complaint.STATUS_HANDLED : Complaint.STATUS_REJECTED);
        complaint.setHandleResult(dto.getHandleResult());
        complaint.setHandlerId(handlerId);
        complaint.setHandleTime(LocalDateTime.now());
        updateById(complaint);

        messageSender.send(complaint.getUserId(), MessageSender.TYPE_ORDER_FINISHED,
                approved ? "售后已处理" : "售后已驳回",
                "您的售后申请（" + typeDesc(complaint.getType()) + "）"
                        + (approved ? "已处理：" : "已驳回：") + defaultResult(dto));
        return complaint;
    }

    /** 售后单详情（userId 非空时校验归属） */
    public Complaint detail(Long complaintId, Long userId) {
        Complaint complaint = requireComplaint(complaintId);
        if (userId != null && !complaint.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return complaint;
    }

    private Complaint requireComplaint(Long complaintId) {
        Complaint complaint = getById(complaintId);
        if (complaint == null) {
            throw new BusinessException("售后单不存在");
        }
        return complaint;
    }

    private static String defaultResult(ComplaintHandleDTO dto) {
        return dto.getHandleResult() == null ? "（无补充说明）" : dto.getHandleResult();
    }

    public static String typeDesc(Integer type) {
        return switch (type) {
            case Complaint.TYPE_COMPLAINT -> "投诉";
            case Complaint.TYPE_REFUND -> "退款";
            case Complaint.TYPE_ACCOUNTABILITY -> "追责";
            case Complaint.TYPE_REISSUE_POINTS -> "补发积分";
            case Complaint.TYPE_ARBITRATION -> "纠纷仲裁";
            default -> String.valueOf(type);
        };
    }
}

package com.dianfei.business.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.im.dto.ImConversationVO;
import com.dianfei.business.im.dto.ImWsFrame;
import com.dianfei.business.im.entity.ImConversation;
import com.dianfei.business.im.mapper.ImConversationMapper;
import com.dianfei.business.recycle.entity.RecycleOrder;
import com.dianfei.business.recycle.mapper.RecycleOrderMapper;
import com.dianfei.business.repair.entity.RepairOrder;
import com.dianfei.business.repair.mapper.RepairOrderMapper;
import com.dianfei.business.staff.entity.BizStaff;
import com.dianfei.business.staff.mapper.BizStaffMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IM 会话（onsite03）：按「bizType + bizId」幂等创建/复用；
 * 成员 = 下单用户 + 接单员工（biz_staff.user_id），非成员 403。
 */
@Service
@RequiredArgsConstructor
public class ImConversationService extends ServiceImpl<ImConversationMapper, ImConversation> {

    private final RecycleOrderMapper recycleOrderMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final BizStaffMapper bizStaffMapper;
    private final SysUserMapper sysUserMapper;
    private final ImPushService imPushService;

    /** 消息类型白名单 */
    private static final List<String> BIZ_TYPES = List.of("RECYCLE", "REPAIR");

    /**
     * 幂等创建/复用会话（C-O3-2）：已存在直接校验成员返回。
     * 发起规则：下单用户须订单已接单；员工在岗且订单已指派给自己、或本片区待接单订单，均可发起（未接单也可沟通）。
     */
    @Transactional
    public ImConversationVO getOrCreate(String bizType, Long bizId, Long userId) {
        if (!BIZ_TYPES.contains(bizType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的会话业务类型：" + bizType);
        }
        ImConversation exists = getOne(new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getBizType, bizType)
                .eq(ImConversation::getBizId, bizId), false);
        if (exists != null) {
            assertMember(exists, userId);
            return toVO(exists, userId);
        }
        // 新建：解析订单双方（未接单时接单员工可为空）
        Long userUserId = resolveUserUserId(bizType, bizId);
        if (userUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单不存在");
        }
        Long staffUserId = null;
        Long staffId = resolveStaffId(bizType, bizId);
        if (staffId != null) {
            BizStaff staff = bizStaffMapper.selectById(staffId);
            staffUserId = staff == null ? null : staff.getUserId();
        }
        boolean isUser = userId.equals(userUserId);
        if (!isUser) {
            // 员工发起：在岗 + 已指派给自己或本片区待接单订单（未接单也可发消息）
            requireStaffEligible(userId, staffId, resolveOrderAreaId(bizType, bizId));
            if (staffUserId == null) {
                staffUserId = userId; // 未接单：会话绑定发起员工
            }
        } else if (staffUserId == null) {
            throw new BusinessException("订单尚未接单，暂无法发起聊天");
        }
        ImConversation conv = new ImConversation();
        conv.setBizType(bizType);
        conv.setBizId(bizId);
        conv.setUserId(userUserId);
        conv.setStaffUserId(staffUserId);
        conv.setUnreadUser(0);
        conv.setUnreadStaff(0);
        save(conv);
        return toVO(conv, userId);
    }

    /** 我的会话列表（user_id 或 staff_user_id 命中，按 last_time 倒序，拼对方昵称/头像） */
    public List<ImConversationVO> listMine(Long userId) {
        List<ImConversation> list = list(new LambdaQueryWrapper<ImConversation>()
                .and(w -> w.eq(ImConversation::getUserId, userId)
                        .or().eq(ImConversation::getStaffUserId, userId))
                .orderByDesc(ImConversation::getLastTime)
                .orderByDesc(ImConversation::getId));
        return toVOs(list, userId);
    }

    /** 已读回执（C-O3-2）：置零本人侧未读，并广播 READ 帧给对方在线连接 */
    @Transactional
    public void markRead(Long convId, Long userId) {
        ImConversation conv = getById(convId);
        if (conv == null) {
            throw new BusinessException("会话不存在");
        }
        assertMember(conv, userId);
        Long peerId = peerOf(conv, userId);
        boolean isUser = userId.equals(conv.getUserId());
        int before = isUser ? conv.getUnreadUser() : conv.getUnreadStaff();
        if (before > 0) {
            if (isUser) {
                conv.setUnreadUser(0);
            } else {
                conv.setUnreadStaff(0);
            }
            updateById(conv);
        }
        // READ 帧：对方在线连接收到后标记「已读」（readerId = 已读者）
        ImWsFrame frame = ImWsFrame.of("READ", convId);
        frame.setReaderId(userId);
        imPushService.pushToUser(peerId, frame);
    }

    /** 未读总数（mine.vue「在线聊天」角标 / 员工端工作台角标） */
    public long unreadTotal(Long userId) {
        List<ImConversation> list = list(new LambdaQueryWrapper<ImConversation>()
                .and(w -> w.eq(ImConversation::getUserId, userId)
                        .or().eq(ImConversation::getStaffUserId, userId)));
        return list.stream()
                .mapToLong(c -> userId.equals(c.getUserId()) ? c.getUnreadUser() : c.getUnreadStaff())
                .sum();
    }

    /** 会话对方 userId（供推送定位） */
    public Long peerOf(ImConversation conv, Long me) {
        return me.equals(conv.getUserId()) ? conv.getStaffUserId() : conv.getUserId();
    }

    /** 成员校验：调用方必须是 user_id 或 staff_user_id，否则 403 */
    public void assertMember(ImConversation conv, Long userId) {
        if (!userId.equals(conv.getUserId()) && !userId.equals(conv.getStaffUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非会话成员");
        }
    }

    /** 会话成员身份：USER / STAFF */
    public String roleOf(ImConversation conv, Long userId) {
        return userId.equals(conv.getUserId()) ? "USER" : "STAFF";
    }

    /** 订单已指派员工 id（biz_staff.id），未接单返回 null */
    private Long resolveStaffId(String bizType, Long bizId) {
        if ("RECYCLE".equals(bizType)) {
            RecycleOrder order = recycleOrderMapper.selectById(bizId);
            return order == null ? null : order.getStaffId();
        }
        RepairOrder order = repairOrderMapper.selectById(bizId);
        return order == null ? null : order.getStaffId();
    }

    /** 订单片区 id（员工发起资格校验用） */
    private Long resolveOrderAreaId(String bizType, Long bizId) {
        if ("RECYCLE".equals(bizType)) {
            RecycleOrder order = recycleOrderMapper.selectById(bizId);
            return order == null ? null : order.getAreaId();
        }
        RepairOrder order = repairOrderMapper.selectById(bizId);
        return order == null ? null : order.getAreaId();
    }

    /** 员工发起资格：在岗员工 + 订单已指派给本人，或本片区待接单订单 */
    private void requireStaffEligible(Long staffUserId, Long orderStaffId, Long orderAreaId) {
        BizStaff staff = bizStaffMapper.selectOne(new LambdaQueryWrapper<BizStaff>()
                .eq(BizStaff::getUserId, staffUserId)
                .eq(BizStaff::getDeleted, 0)
                .orderByDesc(BizStaff::getUpdateTime)
                .last("LIMIT 1"));
        if (staff == null || staff.getStatus() == null || staff.getStatus() != 1
                || staff.getAuditStatus() == null || staff.getAuditStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非在岗员工，无法发起会话");
        }
        boolean assigned = orderStaffId != null && orderStaffId.equals(staff.getId());
        boolean pendingSameArea = orderStaffId == null && orderAreaId != null
                && orderAreaId.equals(staff.getAreaId());
        if (!assigned && !pendingSameArea) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅可对本片区待接单或指派给自己的订单发起会话");
        }
    }

    private Long resolveUserUserId(String bizType, Long bizId) {
        if ("RECYCLE".equals(bizType)) {
            RecycleOrder order = recycleOrderMapper.selectById(bizId);
            return order == null ? null : order.getUserId();
        }
        RepairOrder order = repairOrderMapper.selectById(bizId);
        return order == null ? null : order.getUserId();
    }

    /** 会话 → VO（getOrCreate 单条用） */
    private ImConversationVO toVO(ImConversation conv, Long viewerId) {
        Long peerId = peerOf(conv, viewerId);
        ImConversationVO vo = new ImConversationVO();
        vo.setId(conv.getId());
        vo.setBizType(conv.getBizType());
        vo.setBizId(conv.getBizId());
        vo.setLastMsg(buildLastMsg(conv.getLastMsgType(), conv.getLastMsg()));
        vo.setLastTime(conv.getLastTime());
        vo.setUnread(viewerId.equals(conv.getUserId()) ? conv.getUnreadUser() : conv.getUnreadStaff());
        SysUser peer = sysUserMapper.selectById(peerId);
        if (peer != null) {
            vo.setPeerName(peer.getNickname() == null || peer.getNickname().isBlank()
                    ? peer.getUsername() : peer.getNickname());
            vo.setPeerAvatar(peer.getAvatar());
        } else {
            vo.setPeerName("用户");
        }
        return vo;
    }

    /** 会话列表批量 VO（listMine 用，一次查询对方信息） */
    public List<ImConversationVO> toVOs(List<ImConversation> list, Long viewerId) {
        List<Long> peerIds = list.stream()
                .map(c -> peerOf(c, viewerId))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = peerIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(peerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));
        return list.stream().map(c -> {
            ImConversationVO vo = new ImConversationVO();
            vo.setId(c.getId());
            vo.setBizType(c.getBizType());
            vo.setBizId(c.getBizId());
            vo.setLastMsg(buildLastMsg(c.getLastMsgType(), c.getLastMsg()));
            vo.setLastTime(c.getLastTime());
            vo.setUnread(viewerId.equals(c.getUserId()) ? c.getUnreadUser() : c.getUnreadStaff());
            SysUser peer = userMap.get(peerOf(c, viewerId));
            if (peer != null) {
                vo.setPeerName(peer.getNickname() == null || peer.getNickname().isBlank()
                        ? peer.getUsername() : peer.getNickname());
                vo.setPeerAvatar(peer.getAvatar());
            } else {
                vo.setPeerName("用户");
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /** 列表摘要：文本截断 / 文件类型名 / 表情 key */
    private String buildLastMsg(String msgType, String lastMsg) {
        if (lastMsg == null) {
            return "暂无消息";
        }
        if (msgType == null || "TEXT".equals(msgType)) {
            return lastMsg.length() > 40 ? lastMsg.substring(0, 40) + "…" : lastMsg;
        }
        return switch (msgType) {
            case "IMAGE" -> "[图片]";
            case "STICKER" -> "[表情]";
            case "VOICE" -> "[语音]";
            case "VIDEO" -> "[视频]";
            case "SYSTEM" -> lastMsg.length() > 40 ? lastMsg.substring(0, 40) + "…" : lastMsg;
            default -> "[消息]";
        };
    }
}

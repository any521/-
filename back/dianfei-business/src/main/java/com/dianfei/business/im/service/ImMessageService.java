package com.dianfei.business.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.im.dto.ImMessageVO;
import com.dianfei.business.im.dto.ImSendDTO;
import com.dianfei.business.im.dto.ImWsFrame;
import com.dianfei.business.im.entity.ImConversation;
import com.dianfei.business.im.entity.ImMessage;
import com.dianfei.business.im.mapper.ImMessageMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * IM 消息（onsite03）：发送即落库（先 DB 后推，推送失败不阻塞），历史倒序分页。
 */
@Service
@RequiredArgsConstructor
public class ImMessageService extends ServiceImpl<ImMessageMapper, ImMessage> {

    /** 消息类型白名单 */
    private static final List<String> MSG_TYPES = List.of("TEXT", "IMAGE", "STICKER", "VOICE", "VIDEO");

    private final ImConversationService conversationService;
    private final ImPushService imPushService;
    private final SysUserMapper sysUserMapper;

    /**
     * 发送消息（C-O3-3）：成员校验 → 落库 → 更新会话 last_* + 对方未读+1 → 推对方在线连接。
     */
    @Transactional
    public ImMessageVO send(Long convId, Long senderId, ImSendDTO dto) {
        if (dto == null || dto.getMsgType() == null || !MSG_TYPES.contains(dto.getMsgType())) {
            throw new BusinessException("不支持的消息类型");
        }
        ImConversation conv = conversationService.getById(convId);
        if (conv == null) {
            throw new BusinessException("会话不存在");
        }
        conversationService.assertMember(conv, senderId);
        String senderRole = conversationService.roleOf(conv, senderId);

        ImMessage msg = new ImMessage();
        msg.setConvId(convId);
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setMsgType(dto.getMsgType());
        msg.setContent(dto.getContent() == null ? "" : dto.getContent());
        msg.setDuration(dto.getDuration() == null ? 0 : dto.getDuration());
        msg.setStatus(1);
        save(msg);

        // 更新会话预览 + 对方未读 +1
        Long peerId = conversationService.peerOf(conv, senderId);
        boolean peerIsUser = peerId.equals(conv.getUserId());
        conv.setLastMsgType(dto.getMsgType());
        conv.setLastMsg(dto.getContent());
        conv.setLastTime(msg.getCreateTime() == null ? LocalDateTime.now() : msg.getCreateTime());
        if (peerIsUser) {
            conv.setUnreadUser((conv.getUnreadUser() == null ? 0 : conv.getUnreadUser()) + 1);
        } else {
            conv.setUnreadStaff((conv.getUnreadStaff() == null ? 0 : conv.getUnreadStaff()) + 1);
        }
        conversationService.updateById(conv);

        // 推对方在线连接（不在线则等上线补推，见 ImPushService.pushUnreadOnConnect）
        ImMessageVO vo = toVO(msg);
        fillSender(vo);
        ImWsFrame frame = ImWsFrame.of("MSG", convId);
        frame.setMsg(dto);
        frame.setSenderId(senderId);
        frame.setSenderRole(senderRole);
        frame.setSenderName(vo.getSenderName());
        frame.setSenderAvatar(vo.getSenderAvatar());
        frame.setMessageId(msg.getId());
        frame.setCreateTime(vo.getCreateTime() == null ? ""
                : vo.getCreateTime().toString().replace('T', ' ').substring(0, 16));
        // 只推对方；不推发送者本人——发送端以本地乐观消息为准（前端发送即乐观确认），
        // 推自己会与本地占位叠加（重连重发/多端场景）造成消息重复
        imPushService.pushToUser(peerId, frame);
        return vo;
    }

    /** 历史消息倒序分页（前端倒序渲染；页码越大越旧） */
    public PageVO<ImMessageVO> history(Long convId, Long viewerId, int pageNum, int pageSize) {
        ImConversation conv = conversationService.getById(convId);
        if (conv == null) {
            throw new BusinessException("会话不存在");
        }
        conversationService.assertMember(conv, viewerId);
        Page<ImMessage> page = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ImMessage>()
                        .eq(ImMessage::getConvId, convId)
                        .eq(ImMessage::getStatus, 1)
                        .orderByDesc(ImMessage::getId));
        PageVO<ImMessageVO> vo = new PageVO<>();
        vo.setTotal(page.getTotal());
        vo.setPages(page.getPages());
        List<ImMessageVO> vos = page.getRecords().stream().map(this::toVO).toList();
        fillSenders(vos);
        vo.setList(vos);
        return vo;
    }

    private ImMessageVO toVO(ImMessage msg) {
        return ImMessageVO.of(msg.getId(), msg.getConvId(), msg.getSenderId(), msg.getSenderRole(),
                msg.getMsgType(), msg.getContent(), msg.getDuration(), msg.getCreateTime());
    }

    /** 单条发送者信息填充（send 后推送前用） */
    private void fillSender(ImMessageVO vo) {
        if (vo.getSenderId() == null) {
            return;
        }
        SysUser u = sysUserMapper.selectById(vo.getSenderId());
        applySender(vo, u);
    }

    /** 批量发送者信息填充（history 用，一次查库避免 N+1） */
    private void fillSenders(List<ImMessageVO> vos) {
        List<Long> ids = vos.stream().map(ImMessageVO::getSenderId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        for (ImMessageVO vo : vos) {
            applySender(vo, vo.getSenderId() == null ? null : userMap.get(vo.getSenderId()));
        }
    }

    /** 发送者昵称/头像；查不到（如 SYSTEM）给占位名 */
    private void applySender(ImMessageVO vo, SysUser u) {
        if (u == null) {
            vo.setSenderName("系统");
            return;
        }
        vo.setSenderName(u.getNickname() == null || u.getNickname().isBlank()
                ? u.getUsername() : u.getNickname());
        vo.setSenderAvatar(u.getAvatar());
    }
}

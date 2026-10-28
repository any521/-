package com.dianfei.business.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dianfei.business.im.dto.ImSendDTO;
import com.dianfei.business.im.dto.ImWsFrame;
import com.dianfei.business.im.entity.ImConversation;
import com.dianfei.business.im.mapper.ImConversationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * IM 连接注册表 + 推送（onsite03）：
 * 本批次内存实现（ConcurrentHashMap&lt;userId, Set&lt;session&gt;&gt;），
 * 多实例部署时升级 Redis 发布订阅广播（记录决策：单实例优先，简单可靠）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImPushService {

    private final ImConversationMapper conversationMapper;
    private final ObjectMapper objectMapper;

    /** userId → 在线 WebSocket 会话集合（一用户多端/多标签页） */
    private final Map<Long, Set<WebSocketSession>> registry = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        registry.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("IM 连接注册 userId={} 当前连接数={}", userId, registry.get(userId).size());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = registry.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                registry.remove(userId);
            }
        }
        log.info("IM 连接注销 userId={}", userId);
    }

    /** 是否在线（离线补推判断） */
    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = registry.get(userId);
        return sessions != null && sessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** 推送给用户全部在线连接（同一 session 串行发送，失败连接移除） */
    public void pushToUser(Long userId, ImWsFrame frame) {
        Set<WebSocketSession> sessions = registry.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(frame);
            TextMessage message = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.warn("IM 推送失败 userId={} msg={}", userId, e.getMessage());
                    sessions.remove(session);
                }
            }
        } catch (Exception e) {
            log.error("IM 推送序列化失败 userId={}", userId, e);
        }
    }

    /**
     * 连接建立后的离线补推（C-O3-3 上线补推）：
     * 对该用户存在未读的每个会话推送 SYSTEM 帧（content 为未读条数提示），
     * 前端收到后刷新会话列表/未读角标/当前聊天页历史——以简单可靠为原则，全量拉取走 REST。
     */
    public void pushUnreadOnConnect(Long userId) {
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<ImConversation>()
                .and(w -> w.eq(ImConversation::getUserId, userId).or().eq(ImConversation::getStaffUserId, userId))
                .gt(ImConversation::getUnreadUser, 0);
        // 双侧未读条件分开查（MyBatis-Plus 同 wrapper 内按角色区分较繁，两侧各查一次更清晰）
        for (ImConversation conv : conversationMapper.selectList(wrapper)) {
            pushSystem(conv.getId(), String.format("您有 %d 条未读消息", conv.getUnreadUser()));
        }
        LambdaQueryWrapper<ImConversation> staffWrapper = new LambdaQueryWrapper<ImConversation>()
                .and(w -> w.eq(ImConversation::getUserId, userId).or().eq(ImConversation::getStaffUserId, userId))
                .gt(ImConversation::getUnreadStaff, 0);
        for (ImConversation conv : conversationMapper.selectList(staffWrapper)) {
            pushSystem(conv.getId(), String.format("您有 %d 条未读消息", conv.getUnreadStaff()));
        }
    }

    /** SYSTEM 帧（订单状态变化等，预留） */
    public void pushSystem(Long convId, String content) {
        ImWsFrame frame = ImWsFrame.of("SYSTEM", convId);
        ImSendDTO msg = new ImSendDTO();
        msg.setMsgType("SYSTEM");
        msg.setContent(content);
        msg.setDuration(0);
        frame.setMsg(msg);
        // 系统帧推给会话双方在线连接（成员由调用方保证已解析）
        ImConversation conv = conversationMapper.selectById(convId);
        if (conv != null) {
            pushToUser(conv.getUserId(), frame);
            pushToUser(conv.getStaffUserId(), frame);
        }
    }

    /**
     * 站内信（biz_message）新消息唤醒：无会话上下文，仅通知前端刷新未读角标。
     * DbMessageSender 落库成功后调用；用户不在线时静默丢弃（下次打开消息页 REST 拉取）。
     */
    public void pushNotice(Long userId, String content) {
        if (userId == null) {
            return;
        }
        ImWsFrame frame = ImWsFrame.of("SYSTEM", null);
        ImSendDTO msg = new ImSendDTO();
        msg.setMsgType("SYSTEM");
        msg.setContent(content);
        msg.setDuration(0);
        frame.setMsg(msg);
        pushToUser(userId, frame);
    }
}

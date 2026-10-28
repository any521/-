package com.dianfei.business.im.ws;

import com.dianfei.business.im.dto.ImSendDTO;
import com.dianfei.business.im.dto.ImWsFrame;
import com.dianfei.business.im.entity.ImConversation;
import com.dianfei.business.im.service.ImConversationService;
import com.dianfei.business.im.service.ImMessageService;
import com.dianfei.business.im.service.ImPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * IM 消息处理器（onsite03）：MSG/READ/PING/TYPING 分发；
 * LOCATION 帧为下行专用（onsite04 订阅），上行收到直接忽略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    private final ImPushService imPushService;
    private final ImConversationService conversationService;
    private final ImMessageService messageService;
    private final ObjectMapper objectMapper;

    private Long userIdOf(WebSocketSession session) {
        Object userId = session.getAttributes().get(ImHandshakeInterceptor.ATTR_USER_ID);
        return userId instanceof Long l ? l : null;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignored) {
                // 关闭失败忽略
            }
            return;
        }
        imPushService.register(userId, session);
        // 上线补推未读（SYSTEM 帧提示，前端拉取历史），失败不阻塞连接
        try {
            imPushService.pushUnreadOnConnect(userId);
        } catch (Exception e) {
            log.warn("IM 上线补推失败 userId={} msg={}", userId, e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        Long userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        ImWsFrame frame;
        try {
            frame = objectMapper.readValue(textMessage.getPayload(), ImWsFrame.class);
        } catch (Exception e) {
            log.warn("IM 帧解析失败 userId={} payload={}", userId, textMessage.getPayload());
            return;
        }
        String type = frame.getType() == null ? "" : frame.getType();
        try {
            switch (type) {
                case "MSG" -> {
                    if (frame.getConvId() == null || frame.getMsg() == null) {
                        return;
                    }
                    // 先落库后推送（C-O3-3：推送失败不阻塞），结果下行帧由 send 内部推送对方
                    messageService.send(frame.getConvId(), userId, frame.getMsg());
                }
                case "READ" -> {
                    if (frame.getConvId() == null) {
                        return;
                    }
                    conversationService.markRead(frame.getConvId(), userId);
                }
                case "PING" -> sendPong(session, frame.getConvId());
                case "TYPING" -> forwardTyping(userId, frame);
                default -> log.debug("IM 忽略未知/下行帧类型={} userId={}", type, userId);
            }
        } catch (Exception e) {
            log.warn("IM 处理帧失败 type={} userId={} msg={}", type, userId, e.getMessage());
        }
    }

    private void sendPong(WebSocketSession session, Long convId) {
        ImWsFrame pong = ImWsFrame.of("PONG", convId);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            }
        } catch (Exception e) {
            log.warn("IM PONG 发送失败 {}", e.getMessage());
        }
    }

    /** TYPING 透传会话对方在线连接（成员校验防刷） */
    private void forwardTyping(Long userId, ImWsFrame frame) {
        if (frame.getConvId() == null) {
            return;
        }
        ImConversation conv = conversationService.getById(frame.getConvId());
        if (conv == null) {
            return;
        }
        conversationService.assertMember(conv, userId);
        Long peerId = conversationService.peerOf(conv, userId);
        imPushService.pushToUser(peerId, ImWsFrame.of("TYPING", frame.getConvId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            imPushService.unregister(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("IM 传输异常 userId={} msg={}", userIdOf(session), exception.getMessage());
        Long userId = userIdOf(session);
        if (userId != null) {
            imPushService.unregister(userId, session);
        }
    }
}

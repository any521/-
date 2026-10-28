package com.dianfei.business.message;

import com.dianfei.business.im.service.ImPushService;
import com.dianfei.business.message.entity.BizMessage;
import com.dianfei.business.message.mapper.BizMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 站内信落库实现（B5，biz_message）：@Primary 替换日志桩 LogMessageSender，接口签名不变。
 * 落库后经 IM WS 推 SYSTEM 帧唤醒前端未读角标（用户不在线时静默丢弃）。
 * 发送失败仅告警日志，绝不影响订单/发分主链路（降级优先 D15）。
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class DbMessageSender implements MessageSender {

    private final BizMessageMapper messageMapper;
    private final ImPushService imPushService;

    @Override
    public void send(Long userId, String type, String title, String content) {
        try {
            BizMessage message = new BizMessage();
            message.setUserId(userId);
            message.setMsgType(type);
            message.setTitle(title);
            message.setContent(content);
            message.setReadFlag(BizMessage.UNREAD);
            messageMapper.insert(message);
            // 推送失败不影响主链路：用户下次进消息页/轮询仍能拿到未读
            try {
                imPushService.pushNotice(userId, title);
            } catch (Exception e) {
                log.debug("[Message] 站内信 WS 唤醒失败 userId={} type={}", userId, type, e);
            }
        } catch (Exception e) {
            log.warn("[Message] 站内信落库失败，降级日志留痕 userId={} type={} title={} content={}",
                    userId, type, title, content, e);
        }
    }
}

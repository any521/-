package com.dianfei.business.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信日志桩：仅日志留痕保底（真实通道为 @Primary 的 DbMessageSender 落库 biz_message）
 */
@Slf4j
@Component
public class LogMessageSender implements MessageSender {

    @Override
    public void send(Long userId, String type, String title, String content) {
        log.info("[Message] userId={} type={} title={} content={}", userId, type, title, content);
    }
}

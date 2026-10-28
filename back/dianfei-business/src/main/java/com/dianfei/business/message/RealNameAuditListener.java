package com.dianfei.business.message;

import com.dianfei.common.event.RealNameAuditedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 实名认证审核监听（B5）：发布事件后落库站内消息通知用户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealNameAuditListener {

    private final MessageSender messageSender;

    /** 通过审核 → 发「审核通过」消息给到用户；驳回则不发送（或另定义类型） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRealNameAudited(RealNameAuditedEvent event) {
        if (Boolean.TRUE.equals(event.getPass())) {
            try {
                messageSender.send(event.getUserId(), "REAL_NAME_APPROVED",
                        "实名认证审核通过",
                        "您的实名认证已通过审核，如有疑问请联系客服。");
                log.info("[RealNameAudit] 审核通过消息已发送 userId={}", event.getUserId());
            } catch (Exception e) {
                log.warn("[RealNameAudit] 发送审核通过消息失败 userId={} pass={} remark={}",
                        event.getUserId(), event.getPass(), event.getRemark(), e);
            }
        } else {
            // 驳回也可发消息（可选），这里暂不实现
            log.info("[RealNameAudit] 驳回审核 userId={} pass=false remark={}", event.getUserId(), event.getRemark());
        }
    }
}

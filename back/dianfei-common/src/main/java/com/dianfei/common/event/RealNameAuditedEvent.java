package com.dianfei.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 实名认证审核事件（G1，B5）：审核通过后触发，由业务层监听并发送站内消息通知用户。
 */
@Data
@AllArgsConstructor
public class RealNameAuditedEvent {

    private final Long userId;
    private final Boolean pass;
    private final String remark;
}

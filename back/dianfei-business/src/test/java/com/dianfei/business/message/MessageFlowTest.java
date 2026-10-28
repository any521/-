package com.dianfei.business.message;

import com.dianfei.business.message.entity.BizMessage;
import com.dianfei.business.message.service.MessageService;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B5 站内信单测（H2）：@Primary 落库通道生效 / 四类关键节点消息落库 /
 * 分页过滤 / 未读数 / 单条已读（归属校验）/ 全部已读（对齐 trackB B5）
 */
@SpringBootTest
class MessageFlowTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(90_000L);

    @Autowired
    private MessageSender messageSender;
    @Autowired
    private MessageService messageService;

    @Test
    void primaryChannelIsDbSender() {
        // B5 契约：注入 MessageSender 得到落库实现，业务代码零改动切换真实通道
        assertThat(messageSender).isInstanceOf(DbMessageSender.class);
    }

    @Test
    void fourKeyNodeTypesPersisted() {
        Long userId = USER_SEQ.incrementAndGet();
        messageSender.send(userId, MessageSender.TYPE_DISPATCHED, "新回收单指派", "回收单 RO1 已指派给您");
        messageSender.send(userId, MessageSender.TYPE_ORDER_FINISHED, "回收单已完成", "回收单 RO1 已完成");
        messageSender.send(userId, MessageSender.TYPE_POINTS_ARRIVED, "碳积分到账", "回收减排 +10 分");
        messageSender.send(userId, MessageSender.TYPE_POINTS_FROZEN, "积分冻结", "上报异常，积分冻结待复核");

        assertThat(messageService.unreadCount(userId)).isEqualTo(4);
        PageVO<BizMessage> page = messageService.pageMine(userId, null, null, 1, 10);
        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getList()).extracting(BizMessage::getMsgType).containsExactlyInAnyOrder(
                MessageSender.TYPE_DISPATCHED, MessageSender.TYPE_ORDER_FINISHED,
                MessageSender.TYPE_POINTS_ARRIVED, MessageSender.TYPE_POINTS_FROZEN);

        // 按类型过滤
        PageVO<BizMessage> frozen = messageService.pageMine(userId, MessageSender.TYPE_POINTS_FROZEN, null, 1, 10);
        assertThat(frozen.getTotal()).isEqualTo(1);
        assertThat(frozen.getList().get(0).getTitle()).isEqualTo("积分冻结");
    }

    @Test
    void markReadFlowAndOwnershipGuard() {
        Long owner = USER_SEQ.incrementAndGet();
        Long stranger = USER_SEQ.incrementAndGet();
        messageSender.send(owner, MessageSender.TYPE_POINTS_ARRIVED, "碳积分到账", "+5 分");
        messageSender.send(owner, MessageSender.TYPE_ORDER_FINISHED, "回收单已完成", "RO2 已完成");
        BizMessage first = messageService.pageMine(owner, null, BizMessage.UNREAD, 1, 10).getList().get(0);

        // 非本人标记已读被拒
        assertThatThrownBy(() -> messageService.markRead(first.getId(), stranger))
                .isInstanceOf(BusinessException.class);

        // 单条已读：未读数 2 → 1，readFlag 过滤联动
        messageService.markRead(first.getId(), owner);
        assertThat(messageService.unreadCount(owner)).isEqualTo(1);
        assertThat(messageService.pageMine(owner, null, BizMessage.READ, 1, 10).getTotal()).isEqualTo(1);

        // 全部已读：未读清零
        messageService.markAllRead(owner);
        assertThat(messageService.unreadCount(owner)).isZero();
        assertThat(messageService.pageMine(owner, null, null, 1, 10).getTotal()).isEqualTo(2);
    }
}

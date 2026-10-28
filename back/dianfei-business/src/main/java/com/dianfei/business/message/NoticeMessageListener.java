package com.dianfei.business.message;

import cn.hutool.core.util.StrUtil;
import com.dianfei.business.support.SysUserBriefMapper;
import com.dianfei.common.event.NoticePublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collections;
import java.util.List;

/**
 * 公告发布监听（B5）：通知（noticeType=2）发布后按目标端群发站内消息；
 * 公告（noticeType=1）不群发，仅由首页/全局浮层展示最新一条。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeMessageListener {

    /** 站内信字段长度上限（与表结构一致，超长截断） */
    private static final int TITLE_MAX = 64;
    private static final int CONTENT_MAX = 500;

    private final MessageSender messageSender;
    private final SysUserBriefMapper sysUserBriefMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticePublished(NoticePublishedEvent event) {
        if (event.getNoticeType() == null || event.getNoticeType() != 2) {
            return;
        }
        try {
            List<Long> userIds = resolveUserIds(event.getTarget());
            String title = StrUtil.subPre(event.getTitle(), TITLE_MAX);
            String content = StrUtil.subPre(event.getContent(), CONTENT_MAX);
            for (Long userId : userIds) {
                messageSender.send(userId, MessageSender.TYPE_NOTICE, title, content);
            }
            log.info("[Notice] 通知已群发 target={} 收件人={} title={}", event.getTarget(), userIds.size(), title);
        } catch (Exception e) {
            log.warn("[Notice] 通知群发失败 title={} target={}", event.getTitle(), event.getTarget(), e);
        }
    }

    /** 目标端 → 收件人（AGENT 系统账号不参与） */
    private List<Long> resolveUserIds(String target) {
        String t = StrUtil.blankToDefault(target, "ALL");
        switch (t) {
            case "USER":
                return sysUserBriefMapper.selectIdsByRole("USER");
            case "STAFF":
                return sysUserBriefMapper.selectStaffIds();
            case "ADMIN":
                return sysUserBriefMapper.selectIdsByRole("ADMIN");
            case "ALL":
            default:
                return sysUserBriefMapper.selectAllHumanIds();
        }
    }
}

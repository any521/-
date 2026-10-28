package com.dianfei.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告发布事件（A4→B5）：管理端发布公告/通知后触发。
 * 业务层监听：通知（noticeType=2）群发站内消息给目标端用户；公告（noticeType=1）仅全局展示。
 */
@Data
@AllArgsConstructor
public class NoticePublishedEvent {

    private final Long noticeId;

    /** 1 公告 / 2 通知 */
    private final Integer noticeType;

    private final String title;

    private final String content;

    /** ALL / USER / STAFF / ADMIN */
    private final String target;

    private final LocalDateTime publishTime;
}

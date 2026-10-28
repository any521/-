package com.dianfei.business.home.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最新公告摘要行（G7 首页摘要）：标题 + 发布时间（前端关闭记忆按发布身份识别，新发布必重新弹）。
 */
@Data
public class LatestNoticeBrief {

    private String title;

    private LocalDateTime publishTime;
}

package com.dianfei.business.home.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 公告只读直查（G7 首页摘要）：仅取最新已发布「公告」（notice_type=1，通知走站内信不全局展示），
 * 不与 system 模块耦合。
 */
@Mapper
public interface SysNoticeBriefMapper {

    @Select("SELECT title, publish_time FROM sys_notice "
            + "WHERE status = 1 AND notice_type = 1 AND target IN ('ALL', 'USER') AND deleted = 0 "
            + "ORDER BY publish_time DESC LIMIT 1")
    LatestNoticeBrief selectLatestNotice();
}

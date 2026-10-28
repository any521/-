package com.dianfei.system.service;

import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.system.dto.NoticeDTO;
import com.dianfei.system.entity.SysNotice;

/**
 * 公告服务
 */
public interface NoticeService {

    /** 管理端分页（含下线） */
    PageVO<SysNotice> page(PageDTO page, String keyword, Integer status);

    /** 三端已发布公告（target=ALL 或与端匹配） */
    PageVO<SysNotice> pagePublished(PageDTO page, String clientType);

    void add(NoticeDTO dto);

    void edit(NoticeDTO dto);

    void remove(Long id);
}

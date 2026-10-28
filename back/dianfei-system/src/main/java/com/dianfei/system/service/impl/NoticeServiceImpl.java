package com.dianfei.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.common.event.NoticePublishedEvent;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.dto.NoticeDTO;
import com.dianfei.system.entity.SysNotice;
import com.dianfei.system.mapper.SysNoticeMapper;
import com.dianfei.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 公告实现：管理端全量维护；三端只读已发布且目标端匹配（target=ALL 或与端一致）。
 * 发布时（新增即发布 / 下线转发布）发布 NoticePublishedEvent：
 * 通知（noticeType=2）由业务层监听群发站内消息，公告（noticeType=1）仅全局展示。
 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    /** 已发布 */
    private static final int STATUS_PUBLISHED = 1;

    private final SysNoticeMapper sysNoticeMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageVO<SysNotice> page(PageDTO page, String keyword, Integer status) {
        Page<SysNotice> result = sysNoticeMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<SysNotice>()
                        .like(StrUtil.isNotBlank(keyword), SysNotice::getTitle, keyword)
                        .eq(status != null, SysNotice::getStatus, status)
                        .orderByDesc(SysNotice::getCreateTime));
        return PageVO.of(result);
    }

    @Override
    public PageVO<SysNotice> pagePublished(PageDTO page, String clientType) {
        Page<SysNotice> result = sysNoticeMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()),
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getStatus, STATUS_PUBLISHED)
                        .and(w -> w.eq(SysNotice::getTarget, "ALL")
                                .or(StrUtil.isNotBlank(clientType),
                                        o -> o.eq(SysNotice::getTarget, clientType)))
                        .orderByDesc(SysNotice::getPublishTime));
        return PageVO.of(result);
    }

    @Override
    public void add(NoticeDTO dto) {
        SysNotice notice = BeanUtil.copyProperties(dto, SysNotice.class);
        notice.setId(null);
        boolean publishing = STATUS_PUBLISHED == notice.getStatus();
        if (publishing) {
            notice.setPublishTime(LocalDateTime.now());
        }
        sysNoticeMapper.insert(notice);
        if (publishing) {
            eventPublisher.publishEvent(new NoticePublishedEvent(
                    notice.getId(), notice.getNoticeType(), notice.getTitle(),
                    notice.getContent(), notice.getTarget(), notice.getPublishTime()));
        }
    }

    @Override
    public void edit(NoticeDTO dto) {
        SysNotice old = dto.getId() == null ? null : sysNoticeMapper.selectById(dto.getId());
        if (old == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "公告不存在");
        }
        SysNotice notice = BeanUtil.copyProperties(dto, SysNotice.class);
        // 由下线转发布时刷新发布时间并触发群发
        boolean publishing = STATUS_PUBLISHED == notice.getStatus() && old.getStatus() != STATUS_PUBLISHED;
        if (publishing) {
            notice.setPublishTime(LocalDateTime.now());
        }
        sysNoticeMapper.updateById(notice);
        if (publishing) {
            eventPublisher.publishEvent(new NoticePublishedEvent(
                    notice.getId(), notice.getNoticeType(), notice.getTitle(),
                    notice.getContent(), notice.getTarget(), notice.getPublishTime()));
        }
    }

    @Override
    public void remove(Long id) {
        sysNoticeMapper.deleteById(id);
    }
}

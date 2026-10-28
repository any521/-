package com.dianfei.business.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.message.entity.BizMessage;
import com.dianfei.business.message.mapper.BizMessageMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内信查询/已读（B5）：用户仅可见并操作本人消息。
 */
@Service
public class MessageService extends ServiceImpl<BizMessageMapper, BizMessage> {

    /** 我的消息分页（可按类型/已读状态过滤，倒序） */
    public PageVO<BizMessage> pageMine(Long userId, String msgType, Integer readFlag, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMessage> wrapper = new LambdaQueryWrapper<BizMessage>()
                .eq(BizMessage::getUserId, userId)
                .eq(msgType != null && !msgType.isBlank(), BizMessage::getMsgType, msgType)
                .eq(readFlag != null, BizMessage::getReadFlag, readFlag)
                .orderByDesc(BizMessage::getId);
        return PageVO.of(page(new Page<>(pageNum, pageSize), wrapper));
    }

    /** 未读数（红点角标） */
    public long unreadCount(Long userId) {
        return count(new LambdaQueryWrapper<BizMessage>()
                .eq(BizMessage::getUserId, userId)
                .eq(BizMessage::getReadFlag, BizMessage.UNREAD));
    }

    /** 单条已读（校验归属） */
    @Transactional
    public void markRead(Long messageId, Long userId) {
        BizMessage message = getById(messageId);
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        if (!message.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (message.getReadFlag() != BizMessage.READ) {
            message.setReadFlag(BizMessage.READ);
            updateById(message);
        }
    }

    /** 全部已读 */
    @Transactional
    public void markAllRead(Long userId) {
        update(new LambdaUpdateWrapper<BizMessage>()
                .eq(BizMessage::getUserId, userId)
                .eq(BizMessage::getReadFlag, BizMessage.UNREAD)
                .set(BizMessage::getReadFlag, BizMessage.READ));
    }
}

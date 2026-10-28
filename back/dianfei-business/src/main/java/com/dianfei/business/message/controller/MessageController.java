package com.dianfei.business.message.controller;

import com.dianfei.business.message.entity.BizMessage;
import com.dianfei.business.message.service.MessageService;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信（B5）：三端通用（用户/员工均以登录 userId 收信）——
 * 列表分页 / 未读数 / 单条已读 / 全部已读。
 */
@Tag(name = "站内信")
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "我的消息分页（可按类型/已读状态过滤）")
    @GetMapping("/list")
    public Result<PageVO<BizMessage>> list(@RequestParam(required = false) String msgType,
                                           @RequestParam(required = false) Integer readFlag,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(messageService.pageMine(UserContext.getUserId(), msgType, readFlag, pageNum, pageSize));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount(UserContext.getUserId()));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead(UserContext.getUserId());
        return Result.success();
    }
}

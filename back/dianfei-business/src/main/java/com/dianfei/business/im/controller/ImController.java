package com.dianfei.business.im.controller;

import com.dianfei.business.im.dto.ImConversationCreateDTO;
import com.dianfei.business.im.dto.ImConversationVO;
import com.dianfei.business.im.dto.ImMessageVO;
import com.dianfei.business.im.service.ImConversationService;
import com.dianfei.business.im.service.ImMessageService;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IM 会话/消息 REST（onsite03，C-O3-2 契约）：
 * 用户端与员工端共用（会话成员 = 下单用户 + 接单员工，角色由成员身份推导）。
 */
@Tag(name = "即时通讯 IM")
@RestController
@RequestMapping("/api/im")
@RequiredArgsConstructor
public class ImController {

    private final ImConversationService conversationService;
    private final ImMessageService messageService;

    @Operation(summary = "创建/复用会话（按 bizType+bizId 幂等）")
    @PostMapping("/conversations")
    public Result<ImConversationVO> createConversation(@Valid @RequestBody ImConversationCreateDTO dto) {
        return Result.success(conversationService.getOrCreate(dto.getBizType(), dto.getBizId(), UserContext.getUserId()));
    }

    @Operation(summary = "我的会话列表（含未读）")
    @GetMapping("/conversations")
    public Result<List<ImConversationVO>> listMine() {
        return Result.success(conversationService.listMine(UserContext.getUserId()));
    }

    @Operation(summary = "历史消息分页（倒序，页码越大越旧）")
    @GetMapping("/conversations/{id}/messages")
    public Result<PageVO<ImMessageVO>> history(@PathVariable Long id,
                                               @RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(messageService.history(id, UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "已读回执（置零本人未读 + 广播 READ 帧）")
    @PutMapping("/conversations/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        conversationService.markRead(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "未读总数（角标）")
    @GetMapping("/unread-total")
    public Result<Long> unreadTotal() {
        return Result.success(conversationService.unreadTotal(UserContext.getUserId()));
    }
}

package com.dianfei.system.controller;

import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageDTO;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.LoginUser;
import com.dianfei.common.security.UserContext;
import com.dianfei.system.dto.NoticeDTO;
import com.dianfei.system.entity.SysNotice;
import com.dianfei.system.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告管理（A4）：管理端 CRUD；已发布列表三端通用（按 X-Client-Type 过滤目标端）
 */
@Tag(name = "系统管理-公告")
@RestController
@RequestMapping("/api/system/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "已发布公告列表（三端通用，按当前端过滤）")
    @GetMapping("/published")
    public Result<PageVO<SysNotice>> published(@Valid PageDTO page) {
        LoginUser user = UserContext.get();
        String clientType = user == null ? null : user.getClientType();
        return Result.success(noticeService.pagePublished(page, clientType));
    }

    @Operation(summary = "公告分页列表（管理端，含下线）")
    @RequiresRole("ADMIN")
    @GetMapping("/page")
    public Result<PageVO<SysNotice>> page(@Valid PageDTO page,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        return Result.success(noticeService.page(page, keyword, status));
    }

    @Operation(summary = "新增公告")
    @OperLog(value = "新增公告", module = "系统管理")
    @RequiresRole("ADMIN")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody NoticeDTO dto) {
        noticeService.add(dto);
        return Result.success();
    }

    @Operation(summary = "编辑公告（含发布/下线）")
    @OperLog(value = "编辑公告", module = "系统管理")
    @RequiresRole("ADMIN")
    @PutMapping
    public Result<Void> edit(@Valid @RequestBody NoticeDTO dto) {
        noticeService.edit(dto);
        return Result.success();
    }

    @Operation(summary = "删除公告")
    @OperLog(value = "删除公告", module = "系统管理")
    @RequiresRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        noticeService.remove(id);
        return Result.success();
    }
}

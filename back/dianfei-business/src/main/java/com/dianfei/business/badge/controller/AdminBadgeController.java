package com.dianfei.business.badge.controller;

import com.dianfei.business.badge.dto.BadgeSaveDTO;
import com.dianfei.business.badge.service.BadgeService;
import com.dianfei.business.badge.vo.BadgeVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
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

/**
 * 后台 · 徽章管理（积分市场合并升级）：徽章 CRUD（含图标/封面上传）、上下架；
 * 用户端市场「成就徽章」栏目的唯一维护入口。
 */
@Tag(name = "后台-徽章管理")
@RestController
@RequestMapping("/api/admin/badge")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminBadgeController {

    private final BadgeService badgeService;

    @Operation(summary = "徽章分页（全量含下架，keyword/status 过滤）")
    @GetMapping("/list")
    public Result<PageVO<BadgeVO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status) {
        return Result.success(badgeService.pageAdmin(keyword, status, pageNum, pageSize));
    }

    @Operation(summary = "新增/编辑徽章（code 唯一；图标可上传 /file/upload?biz=badge）")
    @OperLog(value = "保存徽章", module = "徽章管理")
    @PostMapping("/save")
    public Result<BadgeVO> save(@Valid @RequestBody BadgeSaveDTO dto) {
        return Result.success(badgeService.save(dto));
    }

    @Operation(summary = "徽章上/下架")
    @OperLog(value = "徽章上下架", module = "徽章管理")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam boolean onSale) {
        badgeService.changeStatus(id, onSale);
        return Result.success();
    }
}

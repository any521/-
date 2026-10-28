package com.dianfei.business.science.controller;

import com.dianfei.business.science.dto.ScienceArticleCreateDTO;
import com.dianfei.business.science.dto.ScienceAuditDTO;
import com.dianfei.business.science.dto.SciencePointsConfigDTO;
import com.dianfei.business.science.service.ScienceArticleService;
import com.dianfei.business.science.vo.ScienceArticleDetailVO;
import com.dianfei.business.science.vo.ScienceArticleListVO;
import com.dianfei.business.science.vo.ScienceArticleVO;
import com.dianfei.business.science.vo.ScienceDashboardVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 科普文章 API - 管理端（V42）
 */
@Tag(name = "管理端 - 科普专栏")
@RestController
@RequestMapping("/api/system/science")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class ScienceAdminController {

    private final ScienceArticleService articleService;

    @Operation(summary = "管理文章列表")
    @GetMapping("/articles")
    public Result<PageVO<ScienceArticleListVO>> adminPageList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(articleService.adminPageList(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "查看文章详情")
    @GetMapping("/articles/{id}")
    public Result<ScienceArticleDetailVO> articleDetail(@PathVariable Long id) {
        return Result.success(articleService.detail(id));
    }

    @Operation(summary = "管理员上传文章（系统文章，直接发布）")
    @PostMapping("/articles")
    @OperLog(value = "上传科普文章", module = "科普专栏")
    public Result<ScienceArticleVO> create(@Valid @RequestBody ScienceArticleCreateDTO dto) {
        return Result.success(articleService.adminCreate(UserContext.getUserId(), dto));
    }

    @Operation(summary = "管理员编辑系统文章")
    @PutMapping("/articles/{id}")
    @OperLog(value = "编辑科普文章", module = "科普专栏")
    public Result<ScienceArticleVO> update(@PathVariable Long id, @Valid @RequestBody ScienceArticleCreateDTO dto) {
        return Result.success(articleService.adminUpdate(id, dto));
    }

    @Operation(summary = "审核文章")
    @PutMapping("/articles/{id}/audit")
    @OperLog(value = "审核科普文章", module = "科普专栏")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody ScienceAuditDTO dto) {
        articleService.audit(id, dto.getPass(), dto.getRemark(), UserContext.getUserId().toString());
        return Result.success();
    }

    @Operation(summary = "上架/下架文章")
    @PutMapping("/articles/{id}/status")
    @OperLog(value = "上下架科普文章", module = "科普专栏")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        articleService.setStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除文章（软删除）")
    @DeleteMapping("/articles/{id}")
    @OperLog(value = "删除科普文章", module = "科普专栏")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.adminDelete(id);
        return Result.success();
    }

    @Operation(summary = "配置文章积分")
    @PutMapping("/articles/{id}/points-config")
    @OperLog(value = "配置科普文章积分", module = "科普专栏")
    public Result<Void> configurePoints(@PathVariable Long id, @Valid @RequestBody SciencePointsConfigDTO dto) {
        articleService.configurePoints(id, dto.getDownloadPoints(), dto.getBasePoints(), dto.getMultiplier());
        return Result.success();
    }

    @Operation(summary = "批量操作")
    @PutMapping("/articles/batch-action")
    @OperLog(value = "批量操作科普文章", module = "科普专栏")
    public Result<Void> batchAction(@RequestBody List<Long> ids, 
                                    @RequestParam String action,
                                    @RequestParam String operatorId) {
        articleService.batchAction(ids, action, operatorId);
        return Result.success();
    }

    @Operation(summary = "统计看板数据")
    @GetMapping("/dashboard")
    public Result<ScienceDashboardVO> dashboard() {
        ScienceDashboardVO vo = articleService.dashboard();
        return Result.success(vo);
    }
}

package com.dianfei.business.science.controller;

import com.dianfei.business.science.dto.CommentDTO;
import com.dianfei.business.science.dto.RatingDTO;
import com.dianfei.business.science.dto.ScienceArticleCreateDTO;
import com.dianfei.business.science.dto.ScienceAuditDTO;
import com.dianfei.business.science.dto.SciencePointsConfigDTO;
import com.dianfei.business.science.service.ScienceArticleService;
import com.dianfei.business.science.vo.*;
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
 * 科普文章 API - 用户端（V42）
 */
@Tag(name = "用户端 - 科普专栏")
@RestController
@RequestMapping("/api/science")
@RequiredArgsConstructor
public class ScienceUserController {

    private final ScienceArticleService articleService;

    @Operation(summary = "分页查询文章列表")
    @GetMapping("/articles")
    public Result<PageVO<ScienceArticleListVO>> pageList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "view_count") String sortBy,
            @RequestParam(defaultValue = "1") Integer status) {
        return Result.success((PageVO<ScienceArticleListVO>) articleService.pageList(pageNum, pageSize, categoryId, keyword, sortBy, status));
    }

    @Operation(summary = "获取文章详情")
    @GetMapping("/articles/{id}")
    public Result<ScienceArticleDetailVO> detail(@PathVariable Long id) {
        return Result.success(articleService.detail(id));
    }

    @Operation(summary = "阅读文章并增加计数")
    @PostMapping("/articles/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        articleService.read(id);
        return Result.success();
    }

    @Operation(summary = "下载 PDF 文件（扣减积分）")
    @PostMapping("/articles/{id}/download")
    public Result<ScienceDownloadResultVO> download(@PathVariable Long id) {
        return Result.success(articleService.download(id, UserContext.getUserId()));
    }

    @Operation(summary = "创建新文章")
    @PostMapping("/articles")
    public Result<ScienceArticleVO> create(@Valid @RequestBody ScienceArticleCreateDTO dto) {
        ScienceArticleVO vo = articleService.create(UserContext.getUserId(), dto);
        return Result.success(vo);
    }

    @Operation(summary = "编辑文章")
    @PutMapping("/articles/{id}")
    public Result<ScienceArticleVO> update(@PathVariable Long id, @Valid @RequestBody ScienceArticleCreateDTO dto) {
        ScienceArticleVO vo = articleService.update(id, UserContext.getUserId(), dto);
        return Result.success(vo);
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/articles/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "发布文章")
    @PostMapping("/articles/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        articleService.publish(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "撤销发布")
    @PostMapping("/articles/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        articleService.withdraw(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "我的文章列表")
    @GetMapping("/my-articles")
    public Result<Object> myArticles(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(defaultValue = "all") String statusFilter) {
        return Result.success(articleService.myArticles(UserContext.getUserId(), pageNum, pageSize, statusFilter));
    }

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/articles/{id}/like")
    public Result<Void> toggleLike(@PathVariable Long id) {
        articleService.toggleLike(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "取消点赞（幂等，前端取消赞专用）")
    @DeleteMapping("/articles/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        articleService.unlike(id, UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "分页查询文章评论")
    @GetMapping("/comments")
    public Result<PageVO<CommentVO>> comments(@RequestParam Long articleId,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(articleService.commentPage(articleId, pageNum, pageSize));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/articles/{id}/comments")
    public Result<Void> addComment(@PathVariable Long id, @Valid @RequestBody CommentDTO dto) {
        articleService.addComment(id, UserContext.getUserId(), dto.getContent());
        return Result.success();
    }

    @Operation(summary = "动态分类列表")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.success(articleService.categories());
    }

    @Operation(summary = "我的文章统计")
    @GetMapping("/my-stats")
    public Result<MyStatsVO> myStats() {
        return Result.success(articleService.myStats(UserContext.getUserId()));
    }

    @Operation(summary = "提交评分（可覆盖）")
    @PostMapping("/articles/{id}/rating")
    public Result<Void> rate(@PathVariable Long id, @Valid @RequestBody RatingDTO dto) {
        articleService.rate(id, UserContext.getUserId(), dto.getScore(), dto.getComment());
        return Result.success();
    }

    @Operation(summary = "查询当前用户评分状态")
    @GetMapping("/articles/{id}/rating-status")
    public Result<RatingStatusVO> ratingStatus(@PathVariable Long id) {
        return Result.success(articleService.ratingStatus(id, UserContext.getUserId()));
    }
}

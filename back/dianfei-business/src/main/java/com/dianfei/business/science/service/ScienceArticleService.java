package com.dianfei.business.science.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.business.science.dto.ScienceArticleCreateDTO;
import com.dianfei.business.science.entity.ScienceArticle;
import com.dianfei.business.science.vo.ScienceArticleDetailVO;
import com.dianfei.business.science.vo.ScienceArticleListVO;
import com.dianfei.business.science.vo.ScienceDashboardVO;
import com.dianfei.business.science.vo.ScienceDownloadResultVO;
import com.dianfei.business.science.vo.CommentVO;
import com.dianfei.business.science.vo.ScienceArticleVO;
import com.dianfei.business.science.vo.CategoryVO;
import com.dianfei.business.science.vo.MyStatsVO;
import com.dianfei.business.science.vo.RatingStatusVO;
import com.dianfei.common.result.PageVO;

import java.util.List;

/**
 * 科普文章服务（V42）
 */
public interface ScienceArticleService {

    /**
     * 分页查询文章列表（用户端）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param categoryId 分类过滤（可选）
     * @param keyword 关键词搜索
     * @param sortBy 排序方式：view_count/create_time/top_desc
     * @param status 状态过滤：1=已发布
     * @return PageVO<ScienceArticleListVO>
     */
    Object pageList(Integer pageNum, Integer pageSize, String categoryId, 
                   String keyword, String sortBy, Integer status);

    /**
     * 获取文章详情（含作者信息和阅读限制）
     * @param id 文章 ID
     * @return ScienceArticleDetailVO
     */
    ScienceArticleDetailVO detail(Long id);

    /**
     * 阅读文章并增加阅读计数
     * @param id 文章 ID
     */
    void read(Long id);

    /**
     * 下载 PDF 文件（扣减积分）
     * @param id 文章 ID
     * @param userId 用户 ID
     * @return 返回 downloadToken + pointsCost
     */
    ScienceDownloadResultVO download(Long id, Long userId);

    /**
     * 创建文章（用户投稿）
     * @param userId 作者 ID
     * @param dto 创建参数
     * @return ScienceArticleVO
     */
    ScienceArticleVO create(Long userId, ScienceArticleCreateDTO dto);

    /**
     * 编辑文章（仅自己的未发布文章）
     * @param id 文章 ID
     * @param userId 作者 ID
     * @param dto 更新参数
     * @return ScienceArticleVO
     */
    ScienceArticleVO update(Long id, Long userId, ScienceArticleCreateDTO dto);

    /**
     * 删除文章
     * @param id 文章 ID
     * @param userId 作者 ID
     */
    void delete(Long id, Long userId);

    /**
     * 发布文章（草稿→已发布）
     * @param id 文章 ID
     * @param userId 作者 ID
     */
    void publish(Long id, Long userId);

    /**
     * 撤销发布（已发布→草稿）
     * @param id 文章 ID
     * @param userId 作者 ID
     */
    void withdraw(Long id, Long userId);

    /**
     * 我的文章列表（个人中心入口）
     * @param userId 用户 ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param statusFilter 状态过滤：all/draft/published/rejected
     * @return PageVO<ScienceMyArticleVO>
     */
    Object myArticles(Long userId, Integer pageNum, Integer pageSize, String statusFilter);

    /**
     * 点赞/取消点赞
     * @param id 文章 ID
     * @param userId 用户 ID
     */
    void toggleLike(Long id, Long userId);

    /**
     * 管理文章列表（管理端）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param status 状态过滤（可选）
     * @param keyword 关键词搜索
     * @return PageVO<ScienceArticleListVO>
     */
    PageVO<ScienceArticleListVO> adminPageList(Integer pageNum, Integer pageSize, Integer status, String keyword);

    /**
     * 审核文章
     * @param id 文章 ID
     * @param pass 是否通过
     * @param remark 审核意见
     * @param auditBy 审核人 ID
     */
    void audit(Long id, Boolean pass, String remark, String auditBy);

    /**
     * 配置文章积分
     * @param id 文章 ID
     * @param downloadPoints 单次下载积分
     * @param basePoints 基础积分单价
     * @param multiplier 热门系数
     */
    void configurePoints(Long id, Integer downloadPoints, Double basePoints, Double multiplier);

    /**
     * 批量操作（上架/下架/驳回）
     * @param ids 文章 ID 列表
     * @param action 动作：PUBLISH/REJECT/WITHDRAW
     * @param operatorId 操作人 ID
     */
    void batchAction(List<Long> ids, String action, String operatorId);

    /**
     * 统计看板数据（管理端）
     * @return ScienceDashboardVO
     */
    ScienceDashboardVO dashboard();

    /**
     * 管理员上传文章（系统文章，直接发布）
     * @param operatorId 操作人 ID
     * @param dto 创建参数
     * @return ScienceArticleVO
     */
    ScienceArticleVO adminCreate(Long operatorId, ScienceArticleCreateDTO dto);

    /**
     * 管理员编辑文章（仅系统文章）
     * @param id 文章 ID
     * @param dto 更新参数
     * @return ScienceArticleVO
     */
    ScienceArticleVO adminUpdate(Long id, ScienceArticleCreateDTO dto);

    /**
     * 管理员删除文章（软删除，任意状态）
     * @param id 文章 ID
     */
    void adminDelete(Long id);

    /**
     * 管理端上架/下架文章
     * @param id 文章 ID
     * @param status 目标状态：1=上架（已发布），2=下架
     */
    void setStatus(Long id, Integer status);

    /**
     * 分页查询文章评论（用户端）
     * @param articleId 文章 ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return PageVO<CommentVO>
     */
    PageVO<CommentVO> commentPage(Long articleId, Integer pageNum, Integer pageSize);

    /**
     * 发表评论
     * @param articleId 文章 ID
     * @param userId 评论人 ID
     * @param content 评论内容
     */
    void addComment(Long articleId, Long userId, String content);

    /**
     * 取消点赞（幂等）
     * @param id 文章 ID
     * @param userId 用户 ID
     */
    void unlike(Long id, Long userId);

    /**
     * 获取文章分类列表（V51：从文章数据动态提取，按固定字典排序）
     * @return 分类列表
     */
    List<CategoryVO> categories();

    /**
     * 我的文章统计（V51：总数/阅读/下载/预计收益）
     * @param userId 用户 ID
     * @return 统计 VO
     */
    MyStatsVO myStats(Long userId);

    /**
     * 提交评分（V51：已评分则更新，未评分则新增）
     * @param articleId 文章 ID
     * @param userId 评分用户 ID
     * @param score 评分 1-5 星
     * @param comment 短评（可选）
     */
    void rate(Long articleId, Long userId, Integer score, String comment);

    /**
     * 查询当前用户评分状态（V51）
     * @param articleId 文章 ID
     * @param userId 用户 ID（未登录为 null）
     * @return 评分状态 VO
     */
    RatingStatusVO ratingStatus(Long articleId, Long userId);
}

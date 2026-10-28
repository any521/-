package com.dianfei.business.science.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianfei.business.carbon.CarbonLedgerOps;
import com.dianfei.business.carbon.dto.CarbonLedgerResult;
import com.dianfei.business.message.MessageSender;
import com.dianfei.business.science.dto.ScienceArticleCreateDTO;
import com.dianfei.business.science.entity.*;
import com.dianfei.business.science.mapper.*;
import com.dianfei.business.science.service.ScienceArticleService;
import com.dianfei.business.science.vo.*;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.ResultCode;
import com.dianfei.system.entity.SysUser;
import com.dianfei.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 科普文章服务实现（V42）- MVP 简化完整版
 */
@Service
@RequiredArgsConstructor
public class ScienceArticleServiceImpl implements ScienceArticleService {

    private final ScienceArticleMapper articleMapper;
    private final ScienceRatingMapper ratingMapper;
    private final ScienceDownloadLogMapper downloadLogMapper;
    private final ScienceAuthorIncomeMapper incomeMapper;
    private final ScienceLikeMapper likeMapper;
    private final ScienceCommentMapper commentMapper;
    private final SysUserMapper sysUserMapper;
    private final CarbonLedgerOps carbonLedgerService;
    private final MessageSender messageSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageVO<ScienceArticleListVO> pageList(Integer pageNum, Integer pageSize, String categoryId, 
                                                  String keyword, String sortBy, Integer status) {
        Page<ScienceArticle> page = new Page<>((long) pageNum, (long) pageSize);
        
        LambdaQueryWrapper<ScienceArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScienceArticle::getStatus, 1); // 只显示已发布的文章
        
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(ScienceArticle::getTitle, keyword);
        }
        
        // categories 以 JSON 字符串存储，按分类 code 模糊匹配
        if (StrUtil.isNotBlank(categoryId)) {
            wrapper.like(ScienceArticle::getCategories, categoryId);
        }
        
        switch (sortBy) {
            case "download_count":
                wrapper.orderByDesc(ScienceArticle::getDownloadCount);
                break;
            case "like_count":
                wrapper.orderByDesc(ScienceArticle::getLikeCount);
                break;
            default:
                wrapper.orderByDesc(ScienceArticle::getViewCount);
        }
        
        Page<ScienceArticle> result = articleMapper.selectPage(page, wrapper);
        
        List<ScienceArticleListVO> vos = result.getRecords().stream()
            .map(article -> convertToListVO(article))
            .collect(Collectors.toList());
            
        PageVO<ScienceArticleListVO> vo = new PageVO<>();
        vo.setList(vos);
        vo.setTotal(result.getTotal());
        return vo;
    }

    private ScienceArticleListVO convertToListVO(ScienceArticle article) {
        try {
            List<Map<String, String>> categories = objectMapper.readValue(
                article.getCategories(), 
                new TypeReference<List<Map<String, String>>>() {}
            );
            String categoryName = categories.stream()
                .filter(c -> c.containsKey("name"))
                .findFirst()
                .map(c -> c.get("name"))
                .orElse("");
            
            return ScienceArticleListVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .description(article.getDescription())
                .coverUrl(article.getCoverUrl())
                .authorName(article.getAuthorName())
                .viewCount(article.getViewCount() != null ? article.getViewCount() : 0)
                .downloadCount(article.getDownloadCount() != null ? article.getDownloadCount() : 0)
                .likeCount(article.getLikeCount() != null ? article.getLikeCount() : 0)
                .isSystem(article.getIsSystem() != null && article.getIsSystem() == 1)
                .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
                .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
                .isTop(article.getIsTop())
                .status(article.getStatus())
                .categories(categoryName)
                .publishTime(article.getPublishTime() != null ? article.getPublishTime().toString() : null)
                .build();
        } catch (Exception e) {
            return ScienceArticleListVO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .description(article.getDescription())
                .coverUrl(article.getCoverUrl())
                .authorName(article.getAuthorName())
                .status(article.getStatus())
                .categories("")
                .build();
        }
    }

    @Override
    public ScienceArticleDetailVO detail(Long id) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        
        AuthorInfoVO authorInfo = AuthorInfoVO.builder()
            .id(article.getAuthorId())
            .nickname(article.getAuthorName())
            .avatar("")
            .followerCount(0)
            .articleCount(0)
            .build();
            
        RatingInfoVO ratingInfo = buildRatingInfo(id);
        
        return ScienceArticleDetailVO.builder()
            .id(article.getId())
            .title(article.getTitle())
            .description(article.getDescription())
            .content(article.getContent())
            .coverUrl(article.getCoverUrl())
            .pdfFileId(article.getPdfFileId())
            .pdfFilename(article.getPdfFilename())
            .pdfStoragePath(article.getPdfStoragePath())
            .categories(article.getCategories())
            .tags(article.getTags())
            .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
            .status(article.getStatus())
            .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
            .basePoints(article.getBasePoints() != null ? article.getBasePoints() : BigDecimal.valueOf(0.5))
            .multiplier(article.getMultiplier() != null ? article.getMultiplier() : BigDecimal.ONE)
            .viewCount(article.getViewCount() != null ? article.getViewCount() : 0)
            .downloadCount(article.getDownloadCount() != null ? article.getDownloadCount() : 0)
            .likeCount(article.getLikeCount() != null ? article.getLikeCount() : 0)
            .author(authorInfo)
            .rating(ratingInfo)
            .publishTime(article.getPublishTime() != null ? article.getPublishTime().toString() : null)
            .build();
    }

    private RatingInfoVO buildRatingInfo(Long articleId) {
        LambdaQueryWrapper<ScienceRating> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScienceRating::getArticleId, articleId);
        List<ScienceRating> ratings = ratingMapper.selectList(wrapper);
        
        if (ratings.isEmpty()) {
            return null;
        }
        
        double average = ratings.stream()
            .mapToInt(ScienceRating::getScore)
            .average()
            .orElse(0.0);
            
        return RatingInfoVO.builder()
            .average(Double.parseDouble(String.format("%.1f", average)))
            .count(ratings.size())
            .build();
    }

    @Override
    public void read(Long id) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article != null) {
            int viewCount = article.getViewCount() != null ? article.getViewCount() : 0;
            ScienceArticle updates = new ScienceArticle();
            updates.setId(id);
            updates.setViewCount(viewCount + 1);
            articleMapper.updateById(updates);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScienceDownloadResultVO download(Long id, Long userId) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null || article.getAllowDownload() == null || article.getAllowDownload() != 1) {
            throw new BusinessException("文章不存在或禁止下载");
        }
        
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        
        int pointsCost = article.getDownloadPoints() != null ? article.getDownloadPoints() : 0;
        // 官方文章（isSystem=1，平台发布）免费下载：跳过扣费、作者结算与消息；作者下载自己的文章同样免费
        boolean systemArticle = article.getIsSystem() != null && article.getIsSystem() == 1;
        boolean selfDownload = article.getAuthorId() != null && article.getAuthorId().equals(userId);
        boolean chargeable = !systemArticle && !selfDownload && pointsCost > 0;
        
        long balanceAfter = carbonLedgerService.getBalance(userId);
        boolean purchased = false;
        if (chargeable) {
            // 已购判断（日志表为付费购买记录，uk_user_article 唯一约束防并发双扣）：
            // 首次下载真实扣费并结算给作者；重复下载免费，但按产品要求仍校验余额，不足拒绝
            purchased = downloadLogMapper.selectCount(new LambdaQueryWrapper<ScienceDownloadLog>()
                    .eq(ScienceDownloadLog::getUserId, userId)
                    .eq(ScienceDownloadLog::getArticleId, article.getId())) > 0;
            if (!purchased) {
                if (balanceAfter < pointsCost) {
                    throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH);
                }
                ScienceDownloadLog log;
                try {
                    // 先落购买记录取幂等键（每次付费下载唯一），再真实转账，保证转账前主事务写操作全部完成
                    log = insertDownloadLog(article, userId, pointsCost);
                } catch (DuplicateKeyException e) {
                    // 并发下另一请求已落购买记录：按已购处理（免费，仍需余额校验）
                    if (balanceAfter < pointsCost) {
                        throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH);
                    }
                    updateAuthorIncome(article, 0);
                    updateDownloadStats(article);
                    return ScienceDownloadResultVO.builder()
                        .downloadToken(article.getId().toString() + "_" + System.currentTimeMillis())
                        .expiresIn(3600)
                        .pointsCost(0)
                        .balanceAfter((int) balanceAfter)
                        .build();
                }
                updateAuthorIncome(article, pointsCost);
                updateDownloadStats(article);
                // 真实结算：用户→作者原子转账（余额不足抛 2006 由事务整体回滚）
                if (article.getAuthorId() != null && article.getAuthorId() > 0) {
                    CarbonLedgerResult settle = carbonLedgerService.transfer(userId, article.getAuthorId(),
                            pointsCost, "SCIENCE", log.getId(), "科普下载收益：" + article.getTitle());
                    balanceAfter = settle.getBalanceAfter();
                    messageSender.send(article.getAuthorId(), MessageSender.TYPE_POINTS_ARRIVED,
                            "科普下载收益到账", "您的文章《" + article.getTitle() + "》被下载，获得 +" + pointsCost
                                    + " 积分，当前余额 " + carbonLedgerService.getBalance(article.getAuthorId()));
                }
            } else {
                // 已购重下：免费，但积分不足仍拒绝（防止积分清零后无限下载）
                if (balanceAfter < pointsCost) {
                    throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH);
                }
                updateAuthorIncome(article, 0);
                updateDownloadStats(article);
            }
        } else {
            // 免费/官方/作者自下：不落购买记录，仅累计统计
            updateAuthorIncome(article, 0);
            updateDownloadStats(article);
        }
        
        return ScienceDownloadResultVO.builder()
            .downloadToken(article.getId().toString() + "_" + System.currentTimeMillis())
            .expiresIn(3600)
            .pointsCost(chargeable && !purchased ? pointsCost : 0)
            .balanceAfter((int) balanceAfter)
            .build();
    }

    private void updateDownloadStats(ScienceArticle article) {
        int downloadCount = article.getDownloadCount() != null ? article.getDownloadCount() : 0;
        int viewCount = article.getViewCount() != null ? article.getViewCount() : 0;
        
        ScienceArticle updates = new ScienceArticle();
        updates.setId(article.getId());
        updates.setDownloadCount(downloadCount + 1);
        updates.setViewCount(viewCount + 1);
        articleMapper.updateById(updates);
    }

    /** 插入付费购买记录（仅收费文章首次下载调用；雪花 ID 兼作转账幂等键） */
    private ScienceDownloadLog insertDownloadLog(ScienceArticle article, Long userId, int pointsCost) {
        ScienceDownloadLog log = new ScienceDownloadLog();
        log.setId(IdUtil.getSnowflakeNextId());
        log.setArticleId(article.getId());
        log.setUserId(userId);
        log.setPointsCost(pointsCost);
        downloadLogMapper.insert(log);
        return log;
    }

    /** 作者收益按文章累计（uk_article 唯一键：已存在则累加；earnedPoints 仅首次收费下载计入） */
    private void updateAuthorIncome(ScienceArticle article, int earnedPoints) {
        ScienceAuthorIncome exists = incomeMapper.selectOne(
            new LambdaQueryWrapper<ScienceAuthorIncome>()
                .eq(ScienceAuthorIncome::getArticleId, article.getId())
                .last("LIMIT 1")
        );
        if (exists != null) {
            ScienceAuthorIncome updates = new ScienceAuthorIncome();
            updates.setId(exists.getId());
            updates.setTotalDownloads(exists.getTotalDownloads() + 1);
            updates.setTotalPoints(exists.getTotalPoints() + earnedPoints);
            updates.setLastIncomeTime(LocalDateTime.now().toString());
            incomeMapper.updateById(updates);
        } else {
            ScienceAuthorIncome income = new ScienceAuthorIncome();
            income.setId(IdUtil.getSnowflakeNextId());
            income.setArticleId(article.getId());
            income.setAuthorId(article.getAuthorId());
            income.setTotalDownloads(1);
            income.setTotalPoints(earnedPoints);
            income.setLastIncomeTime(LocalDateTime.now().toString());
            incomeMapper.insert(income);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScienceArticleVO create(Long userId, ScienceArticleCreateDTO dto) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BusinessException("标题不能为空");
        }
        if (dto.getPdfFileId() == null || dto.getPdfFileId() <= 0) {
            throw new BusinessException("请上传 PDF 文件");
        }
        
        ScienceArticle article = new ScienceArticle();
        article.setId(IdUtil.getSnowflakeNextId());
        article.setTitle(dto.getTitle().trim());
        article.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        // article.setContent(dto.getContent()); // ScienceArticleCreateDTO 没有这个字段
        article.setCoverUrl(dto.getCoverUrl());
        article.setPdfFileId(dto.getPdfFileId());
        article.setPdfFilename(dto.getPdfFilename());
        article.setPdfStoragePath(dto.getPdfStoragePath()); // 详情页 PDF.js 渲染依赖此路径
        article.setAuthorId(userId);
        article.setAuthorName(dto.getAuthorName() != null && !dto.getAuthorName().trim().isEmpty()
            ? dto.getAuthorName().trim() : "环保达人");
        article.setIsSystem(0);
        article.setAllowDownload(dto.getAllowDownload() != null ? dto.getAllowDownload() : 0);
        article.setStatus(0); // 默认待审核
        article.setIsTop(0);
        article.setDownloadPoints(dto.getDownloadPoints() != null ? dto.getDownloadPoints() : 0);
        article.setBasePoints(dto.getBasePoints() != null ? BigDecimal.valueOf(dto.getBasePoints()) : BigDecimal.valueOf(0.5));
        article.setMultiplier(dto.getMultiplier() != null ? BigDecimal.valueOf(dto.getMultiplier()) : BigDecimal.ONE);
        article.setViewCount(0);
        article.setDownloadCount(0);
        article.setLikeCount(0);
        
        // 处理 categories 和 tags
        if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            try {
                article.setCategories(objectMapper.writeValueAsString(dto.getCategories()));
            } catch (Exception e) {
                article.setCategories(null);
            }
        }
        article.setTags(dto.getTags());
        
        articleMapper.insert(article);
        
        return ScienceArticleVO.builder()
            .id(article.getId())
            .title(article.getTitle())
            .description(article.getDescription())
            .coverUrl(article.getCoverUrl())
            .isSystem(false)
            .status(0)
            .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
            .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
            .viewCount(0)
            .downloadCount(0)
            .publishTime(article.getCreateTime() != null ? article.getCreateTime().toString() : null)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScienceArticleVO update(Long id, Long userId, ScienceArticleCreateDTO dto) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null || !article.getAuthorId().equals(userId)) {
            throw new BusinessException("文章不存在或无权限编辑");
        }
        
        // 仅允许编辑未发布的文章
        if (article.getStatus() != null && article.getStatus() == 1) {
            throw new BusinessException("已发布的文章不能编辑，请先撤销发布");
        }
        
        article.setTitle(dto.getTitle().trim());
        article.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        article.setCoverUrl(dto.getCoverUrl());
        article.setPdfFileId(dto.getPdfFileId());
        article.setPdfFilename(dto.getPdfFilename());
        article.setPdfStoragePath(dto.getPdfStoragePath());
        article.setAllowDownload(dto.getAllowDownload() != null ? dto.getAllowDownload() : 0);
        article.setDownloadPoints(dto.getDownloadPoints() != null ? dto.getDownloadPoints() : 0);
        article.setBasePoints(dto.getBasePoints() != null ? BigDecimal.valueOf(dto.getBasePoints()) : BigDecimal.valueOf(0.5));
        article.setMultiplier(dto.getMultiplier() != null ? BigDecimal.valueOf(dto.getMultiplier()) : BigDecimal.ONE);
        
        if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            try {
                article.setCategories(objectMapper.writeValueAsString(dto.getCategories()));
            } catch (Exception e) {
                article.setCategories(null);
            }
        }
        article.setTags(dto.getTags());
        
        articleMapper.updateById(article);
        
        return ScienceArticleVO.builder()
            .id(article.getId())
            .title(article.getTitle())
            .description(article.getDescription())
            .coverUrl(article.getCoverUrl())
            .isSystem(article.getIsSystem() != null && article.getIsSystem() == 1)
            .status(article.getStatus())
            .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
            .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
            .viewCount(article.getViewCount() != null ? article.getViewCount() : 0)
            .downloadCount(article.getDownloadCount() != null ? article.getDownloadCount() : 0)
            .publishTime(article.getPublishTime() != null ? article.getPublishTime().toString() : null)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null || !article.getAuthorId().equals(userId)) {
            throw new BusinessException("文章不存在或无权限删除");
        }
        
        // 软删除：设置状态为已下架
        article.setStatus(2);
        articleMapper.updateById(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Long userId) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null || !article.getAuthorId().equals(userId)) {
            throw new BusinessException("文章不存在或无权限发布");
        }
        
        // 仅待审核或已下架的文章可以发布
        if (article.getStatus() != null && article.getStatus() != 0 && article.getStatus() != 2) {
            throw new BusinessException("该文章状态不允许发布");
        }
        
        article.setStatus(1);
        article.setPublishTime(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, Long userId) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null || !article.getAuthorId().equals(userId)) {
            throw new BusinessException("文章不存在或无权限撤销");
        }
        
        // 仅已发布的文章可以撤销
        if (article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException("该文章未处于发布状态");
        }
        
        article.setStatus(0);
        articleMapper.updateById(article);
    }

    @Override
    public PageVO<Object> myArticles(Long userId, Integer pageNum, Integer pageSize, String statusFilter) {
        Page<ScienceArticle> page = new Page<>((long) pageNum, (long) pageSize);
        LambdaQueryWrapper<ScienceArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScienceArticle::getAuthorId, userId);
        
        // 按状态过滤
        if (statusFilter != null && !statusFilter.isEmpty() && !"all".equalsIgnoreCase(statusFilter)) {
            try {
                int status = Integer.parseInt(statusFilter);
                wrapper.eq(ScienceArticle::getStatus, status);
            } catch (NumberFormatException e) {
                throw new BusinessException("无效的状态过滤参数");
            }
        }
        
        wrapper.orderByDesc(ScienceArticle::getCreateTime);
        
        Page<ScienceArticle> result = articleMapper.selectPage(page, wrapper);
        
        // 转换为 VO
        List<Object> vos = result.getRecords().stream()
            .map(article -> {
                ScienceMyArticleVO vo = ScienceMyArticleVO.builder()
                    .id(article.getId())
                    .title(article.getTitle())
                    .description(article.getDescription())
                    .coverUrl(article.getCoverUrl())
                    .authorName(article.getAuthorName())
                    .status(article.getStatus())
                    .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
                    .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
                    .viewCount(article.getViewCount() != null ? article.getViewCount() : 0)
                    .downloadCount(article.getDownloadCount() != null ? article.getDownloadCount() : 0)
                    .likeCount(article.getLikeCount() != null ? article.getLikeCount() : 0)
                    .publishTime(article.getPublishTime() != null ? article.getPublishTime().toString() : null)
                    .auditRemark(article.getAuditRemark())
                    .build();
                return (Object) vo;
            })
            .collect(Collectors.toList());
        
        PageVO<Object> vo = new PageVO<>();
        vo.setList(vos);
        vo.setTotal(result.getTotal());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleLike(Long id, Long userId) {
        ScienceLike exists = likeMapper.selectOne(
            new LambdaQueryWrapper<ScienceLike>()
                .eq(ScienceLike::getArticleId, id)
                .eq(ScienceLike::getUserId, userId)
        );
        
        if (exists != null) {
            likeMapper.deleteById(exists.getId());
            decrementLikeCount(id);
        } else {
            ScienceLike like = new ScienceLike();
            like.setId(IdUtil.getSnowflakeNextId());
            like.setArticleId(id);
            like.setUserId(userId);
            likeMapper.insert(like);
            incrementLikeCount(id);
        }
    }

    private void incrementLikeCount(Long id) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article != null) {
            int likeCount = article.getLikeCount() != null ? article.getLikeCount() : 0;
            ScienceArticle updates = new ScienceArticle();
            updates.setId(id);
            updates.setLikeCount(likeCount + 1);
            articleMapper.updateById(updates);
        }
    }

    private void decrementLikeCount(Long id) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article != null) {
            int likeCount = article.getLikeCount() != null ? article.getLikeCount() : 0;
            if (likeCount > 0) {
                ScienceArticle updates = new ScienceArticle();
                updates.setId(id);
                updates.setLikeCount(likeCount - 1);
                articleMapper.updateById(updates);
            }
        }
    }

    @Override
    public PageVO<ScienceArticleListVO> adminPageList(Integer pageNum, Integer pageSize, Integer status, String keyword) {
        Page<ScienceArticle> page = new Page<>((long) pageNum, (long) pageSize);
        LambdaQueryWrapper<ScienceArticle> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null && status >= 0) {
            wrapper.eq(ScienceArticle::getStatus, status);
        }
        
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(ScienceArticle::getTitle, keyword);
        }
        
        wrapper.orderByDesc(ScienceArticle::getCreateTime);
        
        Page<ScienceArticle> result = articleMapper.selectPage(page, wrapper);
        
        List<ScienceArticleListVO> vos = result.getRecords().stream()
            .map(article -> convertToListVO(article))
            .collect(Collectors.toList());
        
        PageVO<ScienceArticleListVO> vo = new PageVO<>();
        vo.setList(vos);
        vo.setTotal(result.getTotal());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, Boolean pass, String remark, String auditBy) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null || article.getStatus() != 0) {
            throw new BusinessException("文章不存在或未处于待审状态");
        }
        
        ScienceArticle updates = new ScienceArticle();
        updates.setId(id);
        updates.setStatus(pass ? 1 : 3);
        updates.setAuditRemark(remark);
        updates.setAuditTime(LocalDateTime.now().toString());
        updates.setAuditBy(auditBy);
        if (pass) {
            updates.setPublishTime(LocalDateTime.now());
        }
        articleMapper.updateById(updates);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void configurePoints(Long id, Integer downloadPoints, Double basePoints, Double multiplier) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        
        ScienceArticle updates = new ScienceArticle();
        updates.setId(id);
        updates.setDownloadPoints(downloadPoints);
        updates.setBasePoints(BigDecimal.valueOf(basePoints));
        updates.setMultiplier(BigDecimal.valueOf(multiplier));
        articleMapper.updateById(updates);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAction(List<Long> ids, String action, String operatorId) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要操作的文章");
        }
        
        for (Long id : ids) {
            ScienceArticle article = articleMapper.selectById(id);
            if (article != null) {
                ScienceArticle updates = new ScienceArticle();
                updates.setId(id);
                
                switch (action) {
                    case "PUBLISH":
                        if (article.getStatus() == 0) {
                            updates.setStatus(1);
                        }
                        break;
                    case "WITHDRAW":
                        if (article.getStatus() == 1) {
                            updates.setStatus(2);
                        }
                        break;
                }
                
                if (updates.getStatus() != null) {
                    articleMapper.updateById(updates);
                }
            }
        }
    }

    @Override
    public ScienceDashboardVO dashboard() {
        long totalArticles = articleMapper.selectCount(null);
        long pendingAudit = articleMapper.selectCount(
            new LambdaQueryWrapper<ScienceArticle>().eq(ScienceArticle::getStatus, 0));
        long publishedArticles = articleMapper.selectCount(
            new LambdaQueryWrapper<ScienceArticle>().eq(ScienceArticle::getStatus, 1));
        long systemArticles = articleMapper.selectCount(
            new LambdaQueryWrapper<ScienceArticle>().eq(ScienceArticle::getIsSystem, 1));
        
        List<ScienceArticle> all = articleMapper.selectList(null);
        int totalViews = all.stream()
            .mapToInt(a -> a.getViewCount() != null ? a.getViewCount() : 0).sum();
        int totalDownloads = all.stream()
            .mapToInt(a -> a.getDownloadCount() != null ? a.getDownloadCount() : 0).sum();
        long totalAuthors = all.stream()
            .map(ScienceArticle::getAuthorId).filter(java.util.Objects::nonNull).distinct().count();
        
        OverviewVO overview = OverviewVO.builder()
            .totalArticles((int) totalArticles)
            .systemArticles((int) systemArticles)
            .userArticles((int) (totalArticles - systemArticles))
            .pendingAudit((int) pendingAudit)
            .publishedArticles((int) publishedArticles)
            .totalViews(totalViews)
            .totalDownloads(totalDownloads)
            .totalAuthors((int) totalAuthors)
            .build();
        
        // 热门文章 top 5（按阅读量）
        Page<ScienceArticle> topPage = new Page<>(1, 5);
        LambdaQueryWrapper<ScienceArticle> topWrapper = new LambdaQueryWrapper<>();
        topWrapper.eq(ScienceArticle::getStatus, 1).orderByDesc(ScienceArticle::getViewCount);
        List<ScienceArticleListVO> topArticles = articleMapper.selectPage(topPage, topWrapper)
            .getRecords().stream()
            .map(this::convertToListVO)
            .collect(Collectors.toList());
        
        // 热门作者 top 5（按累计收益）
        List<TopAuthorVO> topAuthors = new java.util.ArrayList<>();
        Page<ScienceAuthorIncome> incomePage = new Page<>(1, 5);
        incomePage = incomeMapper.selectPage(incomePage,
            new LambdaQueryWrapper<ScienceAuthorIncome>().orderByDesc(ScienceAuthorIncome::getTotalPoints));
        for (ScienceAuthorIncome income : incomePage.getRecords()) {
            ScienceArticle authorArticle = articleMapper.selectOne(
                new LambdaQueryWrapper<ScienceArticle>()
                    .eq(ScienceArticle::getAuthorId, income.getAuthorId())
                    .last("LIMIT 1"));
            topAuthors.add(TopAuthorVO.builder()
                .userId(income.getAuthorId())
                .nickname(authorArticle != null ? authorArticle.getAuthorName() : "用户" + income.getAuthorId())
                .articleCount((int) (long) articleMapper.selectCount(
                    new LambdaQueryWrapper<ScienceArticle>()
                        .eq(ScienceArticle::getAuthorId, income.getAuthorId())))
                .totalIncome(income.getTotalPoints())
                .build());
        }
        
        return ScienceDashboardVO.builder()
            .overview(overview)
            .topArticles(topArticles)
            .topAuthors(topAuthors)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScienceArticleVO adminCreate(Long operatorId, ScienceArticleCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BusinessException("标题不能为空");
        }
        if (dto.getPdfFileId() == null || dto.getPdfFileId() <= 0) {
            throw new BusinessException("请上传 PDF 文件");
        }
        
        ScienceArticle article = new ScienceArticle();
        article.setId(IdUtil.getSnowflakeNextId());
        article.setTitle(dto.getTitle().trim());
        article.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        article.setCoverUrl(dto.getCoverUrl());
        article.setPdfFileId(dto.getPdfFileId());
        article.setPdfFilename(dto.getPdfFilename());
        article.setPdfStoragePath(dto.getPdfStoragePath());
        article.setAuthorId(operatorId != null ? operatorId : 0L);
        article.setAuthorName(dto.getAuthorName() != null && !dto.getAuthorName().trim().isEmpty()
            ? dto.getAuthorName().trim() : "平台官方");
        article.setIsSystem(1); // 系统文章
        article.setAllowDownload(dto.getAllowDownload() != null ? dto.getAllowDownload() : 1);
        article.setStatus(1); // 管理员上传直接发布
        article.setIsTop(dto.getIsTop() != null ? dto.getIsTop() : 0);
        article.setDownloadPoints(dto.getDownloadPoints() != null ? dto.getDownloadPoints() : 0);
        article.setBasePoints(dto.getBasePoints() != null ? BigDecimal.valueOf(dto.getBasePoints()) : BigDecimal.valueOf(0.5));
        article.setMultiplier(dto.getMultiplier() != null ? BigDecimal.valueOf(dto.getMultiplier()) : BigDecimal.ONE);
        article.setViewCount(0);
        article.setDownloadCount(0);
        article.setLikeCount(0);
        article.setPublishTime(LocalDateTime.now());
        
        if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            try {
                article.setCategories(objectMapper.writeValueAsString(dto.getCategories()));
            } catch (Exception e) {
                article.setCategories(null);
            }
        }
        article.setTags(dto.getTags());
        
        articleMapper.insert(article);
        
        return ScienceArticleVO.builder()
            .id(article.getId())
            .title(article.getTitle())
            .description(article.getDescription())
            .coverUrl(article.getCoverUrl())
            .isSystem(true)
            .status(1)
            .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
            .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
            .viewCount(0)
            .downloadCount(0)
            .publishTime(article.getPublishTime().toString())
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScienceArticleVO adminUpdate(Long id, ScienceArticleCreateDTO dto) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (article.getIsSystem() == null || article.getIsSystem() != 1) {
            throw new BusinessException("仅系统文章允许管理员编辑");
        }
        
        article.setTitle(dto.getTitle().trim());
        article.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        article.setCoverUrl(dto.getCoverUrl());
        article.setPdfFileId(dto.getPdfFileId());
        article.setPdfFilename(dto.getPdfFilename());
        article.setPdfStoragePath(dto.getPdfStoragePath());
        article.setAllowDownload(dto.getAllowDownload() != null ? dto.getAllowDownload() : 1);
        article.setDownloadPoints(dto.getDownloadPoints() != null ? dto.getDownloadPoints() : 0);
        article.setBasePoints(dto.getBasePoints() != null ? BigDecimal.valueOf(dto.getBasePoints()) : BigDecimal.valueOf(0.5));
        article.setMultiplier(dto.getMultiplier() != null ? BigDecimal.valueOf(dto.getMultiplier()) : BigDecimal.ONE);
        
        if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            try {
                article.setCategories(objectMapper.writeValueAsString(dto.getCategories()));
            } catch (Exception e) {
                article.setCategories(null);
            }
        }
        article.setTags(dto.getTags());
        
        articleMapper.updateById(article);
        
        return ScienceArticleVO.builder()
            .id(article.getId())
            .title(article.getTitle())
            .description(article.getDescription())
            .coverUrl(article.getCoverUrl())
            .isSystem(true)
            .status(article.getStatus())
            .allowDownload(article.getAllowDownload() != null && article.getAllowDownload() == 1)
            .downloadPoints(article.getDownloadPoints() != null ? article.getDownloadPoints() : 0)
            .viewCount(article.getViewCount() != null ? article.getViewCount() : 0)
            .downloadCount(article.getDownloadCount() != null ? article.getDownloadCount() : 0)
            .publishTime(article.getPublishTime() != null ? article.getPublishTime().toString() : null)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        articleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, Integer status) {
        ScienceArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException("非法状态：仅支持上架(1)/下架(2)");
        }
        
        ScienceArticle updates = new ScienceArticle();
        updates.setId(id);
        updates.setStatus(status);
        if (status == 1) {
            updates.setPublishTime(LocalDateTime.now());
        }
        articleMapper.updateById(updates);
    }

    @Override
    public PageVO<CommentVO> commentPage(Long articleId, Integer pageNum, Integer pageSize) {
        Page<ScienceComment> page = new Page<>((long) pageNum, (long) pageSize);
        LambdaQueryWrapper<ScienceComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScienceComment::getArticleId, articleId)
                .eq(ScienceComment::getStatus, 1)
                .orderByDesc(ScienceComment::getCreateTime);
        Page<ScienceComment> result = commentMapper.selectPage(page, wrapper);

        Map<Long, SysUser> userMap = userMap(result.getRecords().stream()
                .map(ScienceComment::getUserId).distinct().collect(Collectors.toList()));

        List<CommentVO> vos = result.getRecords().stream().map(c -> {
            SysUser user = userMap.get(c.getUserId());
            return CommentVO.builder()
                    .id(String.valueOf(c.getId()))
                    .content(c.getContent())
                    .createTime(c.getCreateTime())
                    .user(CommentVO.UserBrief.builder()
                            .nickname(user == null ? "用户" : displayName(user))
                            .avatar(user == null ? null : user.getAvatar())
                            .build())
                    .build();
        }).collect(Collectors.toList());

        PageVO<CommentVO> vo = new PageVO<>();
        vo.setList(vos);
        vo.setTotal(result.getTotal());
        return vo;
    }

    private Map<Long, SysUser> userMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private static String displayName(SysUser user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(Long articleId, Long userId, String content) {
        if (articleMapper.selectById(articleId) == null) {
            throw new BusinessException("文章不存在");
        }
        ScienceComment comment = new ScienceComment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setStatus(1);
        commentMapper.insert(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long id, Long userId) {
        ScienceLike exists = likeMapper.selectOne(
            new LambdaQueryWrapper<ScienceLike>()
                .eq(ScienceLike::getArticleId, id)
                .eq(ScienceLike::getUserId, userId)
        );
        if (exists != null) {
            likeMapper.deleteById(exists.getId());
            decrementLikeCount(id);
        }
    }

    @Override
    public List<CategoryVO> categories() {
        // 固定字典顺序（与历史分类一致）；文章数据中出现的分类按字典排序，未收录的分类追加到末尾
        List<String> order = List.of(
            "垃圾分类", "资源回收", "电子垃圾", "有害垃圾", "厨余垃圾", "包装回收",
            "海洋保护", "循环经济", "汽车回收", "节能减排", "生活方式", "其他垃圾", "垃圾");
        List<ScienceArticle> all = articleMapper.selectList(null);
        Map<String, String> collected = new java.util.LinkedHashMap<>();
        for (ScienceArticle article : all) {
            if (StrUtil.isBlank(article.getCategories())) {
                continue;
            }
            try {
                List<Map<String, String>> cats = objectMapper.readValue(
                    article.getCategories(), new TypeReference<List<Map<String, String>>>() {});
                for (Map<String, String> cat : cats) {
                    String code = cat.getOrDefault("code", "");
                    String name = cat.getOrDefault("name", code);
                    if (StrUtil.isNotBlank(code) && !collected.containsKey(code)) {
                        collected.put(code, name);
                    }
                }
            } catch (Exception ignored) {
                // 单篇分类解析失败不影响整体分类列表
            }
        }
        List<CategoryVO> result = new java.util.ArrayList<>();
        for (String code : order) {
            if (collected.containsKey(code)) {
                result.add(CategoryVO.builder().code(code).name(collected.remove(code)).build());
            }
        }
        collected.forEach((code, name) -> result.add(CategoryVO.builder().code(code).name(name).build()));
        return result;
    }

    @Override
    public MyStatsVO myStats(Long userId) {
        List<ScienceArticle> mine = articleMapper.selectList(
            new LambdaQueryWrapper<ScienceArticle>().eq(ScienceArticle::getAuthorId, userId));
        int totalViews = 0;
        int totalDownloads = 0;
        double income = 0;
        for (ScienceArticle article : mine) {
            int downloads = article.getDownloadCount() != null ? article.getDownloadCount() : 0;
            totalViews += article.getViewCount() != null ? article.getViewCount() : 0;
            totalDownloads += downloads;
            BigDecimal base = article.getBasePoints() != null ? article.getBasePoints() : BigDecimal.valueOf(0.5);
            BigDecimal multiplier = article.getMultiplier() != null ? article.getMultiplier() : BigDecimal.ONE;
            income += base.multiply(multiplier).multiply(BigDecimal.valueOf(downloads)).doubleValue();
        }
        return MyStatsVO.builder()
            .totalArticles(mine.size())
            .totalViews(totalViews)
            .totalDownloads(totalDownloads)
            .estimatedIncome(Math.round(income * 100.0) / 100.0)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rate(Long articleId, Long userId, Integer score, String comment) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (articleMapper.selectById(articleId) == null) {
            throw new BusinessException("文章不存在");
        }
        ScienceRating exists = ratingMapper.selectOne(
            new LambdaQueryWrapper<ScienceRating>()
                .eq(ScienceRating::getArticleId, articleId)
                .eq(ScienceRating::getUserId, userId)
        );
        if (exists != null) {
            exists.setScore(score);
            exists.setComment(comment);
            ratingMapper.updateById(exists);
        } else {
            ScienceRating rating = new ScienceRating();
            rating.setId(IdUtil.getSnowflakeNextId());
            rating.setArticleId(articleId);
            rating.setUserId(userId);
            rating.setScore(score);
            rating.setComment(comment);
            ratingMapper.insert(rating);
        }
    }

    @Override
    public RatingStatusVO ratingStatus(Long articleId, Long userId) {
        if (userId == null) {
            return RatingStatusVO.builder().rated(false).score(0).build();
        }
        ScienceRating exists = ratingMapper.selectOne(
            new LambdaQueryWrapper<ScienceRating>()
                .eq(ScienceRating::getArticleId, articleId)
                .eq(ScienceRating::getUserId, userId)
        );
        return RatingStatusVO.builder()
            .rated(exists != null)
            .score(exists != null ? exists.getScore() : 0)
            .build();
    }
}

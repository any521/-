package com.dianfei.business.diy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianfei.business.diy.dto.DiyPostCreateDTO;
import com.dianfei.business.diy.entity.DiyLike;
import com.dianfei.business.diy.entity.DiyPost;
import com.dianfei.business.diy.mapper.DiyLikeMapper;
import com.dianfei.business.diy.mapper.DiyPostMapper;
import com.dianfei.business.diy.vo.DiyAreaAggVO;
import com.dianfei.business.diy.vo.DiyLikeResultVO;
import com.dianfei.business.diy.vo.DiyPostVO;
import com.dianfei.business.diy.vo.DiyRankResultVO;
import com.dianfei.business.diy.vo.DiyRankVO;
import com.dianfei.business.staff.entity.BizArea;
import com.dianfei.business.staff.service.AreaService;
import com.dianfei.business.support.SysUserBriefMapper;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 变废为宝 DIY 社区（LoopFill B6）：发布 / 列表（点赞倒序+昵称+已赞态）/ 点赞幂等切换 / 片区环保榜。
 * 排行榜聚合为 diy 包内 Mapper 直查（只读），不改既有 recycle/kitchen/carbon Service 语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiyPostService extends ServiceImpl<DiyPostMapper, DiyPost> {

    private final DiyLikeMapper likeMapper;
    private final AreaService areaService;
    private final SysUserBriefMapper sysUserBriefMapper;

    /** 发布作品（POST /api/diy/posts，Controller 层 @Idempotent 防重） */
    @Transactional
    public DiyPost create(Long userId, DiyPostCreateDTO dto) {
        String image = dto.getImageUrl();
        if (image != null && image.isBlank()) {
            image = null;
        }
        DiyPost post = new DiyPost();
        post.setUserId(userId);
        post.setTitle(dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setImageUrl(image);
        post.setLikes(0);
        save(post);
        return post;
    }

    /** 作品列表分页（点赞数倒序 + 创建时间倒序），批量回填作者昵称与当前用户已赞态 */
    public PageVO<DiyPostVO> page(Long userId, int pageNum, int pageSize) {
        Page<DiyPost> p = page(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<DiyPost>()
                .orderByDesc(DiyPost::getLikes)
                .orderByDesc(DiyPost::getCreateTime));
        List<DiyPost> rows = p.getRecords();
        if (rows.isEmpty()) {
            return PageVO.of(p.getTotal(), p.getPages(), List.of());
        }
        Set<Long> postIds = rows.stream().map(DiyPost::getId).collect(Collectors.toSet());
        Map<Long, String> nicknames = sysUserBriefMapper.selectNicknameByIds(
                        rows.stream().map(DiyPost::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUserBriefMapper.UserBrief::getId,
                        SysUserBriefMapper.UserBrief::getNickname, (a, b) -> a));
        Set<Long> likedIds = likeMapper.selectList(new LambdaQueryWrapper<DiyLike>()
                        .eq(DiyLike::getUserId, userId)
                        .in(DiyLike::getPostId, postIds))
                .stream().map(DiyLike::getPostId).collect(Collectors.toSet());
        List<DiyPostVO> vos = rows.stream().map(post -> DiyPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(nicknames.getOrDefault(post.getUserId(), "环保达人"))
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .likes(post.getLikes())
                .likedByMe(likedIds.contains(post.getId()))
                .createTime(post.getCreateTime())
                .build()).toList();
        return PageVO.of(p.getTotal(), p.getPages(), vos);
    }

    /** 管理端作品分页（创建时间倒序 + 昵称回填；keyword 匹配标题/内容/作者昵称/用户名） */
    public PageVO<DiyPostVO> adminPage(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<DiyPost> qw = new LambdaQueryWrapper<DiyPost>()
                .orderByDesc(DiyPost::getCreateTime);
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> {
                w.like(DiyPost::getTitle, kw).or().like(DiyPost::getContent, kw);
                List<Long> ids = sysUserBriefMapper.selectIdsByKeyword(kw);
                if (!ids.isEmpty()) {
                    w.or().in(DiyPost::getUserId, ids);
                }
            });
        }
        Page<DiyPost> p = page(new Page<>(pageNum, pageSize), qw);
        List<DiyPost> rows = p.getRecords();
        if (rows.isEmpty()) {
            return PageVO.of(p.getTotal(), p.getPages(), List.of());
        }
        Map<Long, String> nicknames = sysUserBriefMapper.selectNicknameByIds(
                        rows.stream().map(DiyPost::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUserBriefMapper.UserBrief::getId,
                        SysUserBriefMapper.UserBrief::getNickname, (a, b) -> a));
        List<DiyPostVO> vos = rows.stream().map(post -> DiyPostVO.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .nickname(nicknames.getOrDefault(post.getUserId(), "环保达人"))
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .likes(post.getLikes())
                .likedByMe(false)
                .createTime(post.getCreateTime())
                .build()).toList();
        return PageVO.of(p.getTotal(), p.getPages(), vos);
    }

    /** 管理端删除作品（软删除，社区列表/详情同步消失） */
    public void adminDelete(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("作品不存在或已删除");
        }
    }

    /**
     * 点赞/取消点赞（POST /api/diy/posts/{id}/like，幂等切换）：
     * 未赞 → uk 插入成功 + likes+1；已赞 → 物理删除 + likes-1（likes>0 防负）；
     * 并发冲突（重复插入/重复删除）按当前已赞态返回，计数由条件更新保证一致。
     */
    @Transactional
    public DiyLikeResultVO like(Long postId, Long userId) {
        if (getById(postId) == null) {
            throw new BusinessException("作品不存在或已删除");
        }
        DiyLike like = new DiyLike();
        like.setPostId(postId);
        like.setUserId(userId);
        boolean inserted;
        try {
            inserted = likeMapper.insert(like) > 0;
        } catch (DuplicateKeyException e) {
            inserted = false;
        }
        if (inserted) {
            baseMapper.incrLikes(postId);
        } else {
            int deleted = likeMapper.delete(new LambdaQueryWrapper<DiyLike>()
                    .eq(DiyLike::getPostId, postId)
                    .eq(DiyLike::getUserId, userId));
            if (deleted > 0) {
                baseMapper.decrLikes(postId);
                return DiyLikeResultVO.builder().liked(false).likes(currentLikes(postId)).build();
            }
            // 并发下已被其他请求取消：保持已赞态，返回当前计数
        }
        return DiyLikeResultVO.builder().liked(true).likes(currentLikes(postId)).build();
    }

    /** 片区环保榜（GET /api/diy/rank）：回收 kg + 垃圾投放袋数 + 碳减排 kg 三指标聚合 + 我的排名 */
    public DiyRankResultVO rank(Long userId) {
        Map<Long, BigDecimal> recycleKg = toValueMap(baseMapper.aggregateRecycleKg());
        Map<Long, BigDecimal> kitchenBags = toValueMap(baseMapper.aggregateKitchenBags());
        Map<Long, BigDecimal> carbonKg = toValueMap(baseMapper.aggregateCarbonKg());
        Set<Long> areaIds = new HashSet<>();
        areaIds.addAll(recycleKg.keySet());
        areaIds.addAll(kitchenBags.keySet());
        areaIds.addAll(carbonKg.keySet());
        if (areaIds.isEmpty()) {
            return DiyRankResultVO.builder().list(List.of()).myAreaRank(null).build();
        }
        Map<Long, String> areaNames = areaService.listByIds(areaIds).stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 1)
                .collect(Collectors.toMap(BizArea::getId, BizArea::getAreaName, (a, b) -> a));
        List<DiyRankVO> list = areaIds.stream()
                .filter(areaNames::containsKey)
                .map(areaId -> {
                    BigDecimal recycle = recycleKg.getOrDefault(areaId, BigDecimal.ZERO);
                    BigDecimal kitchen = kitchenBags.getOrDefault(areaId, BigDecimal.ZERO);
                    BigDecimal carbon = carbonKg.getOrDefault(areaId, BigDecimal.ZERO);
                    return DiyRankVO.builder()
                            .areaId(areaId)
                            .areaName(areaNames.get(areaId))
                            .recycleKg(recycle.setScale(2, RoundingMode.HALF_UP))
                            .kitchenBags(kitchen.longValue())
                            .carbonKg(carbon.setScale(2, RoundingMode.HALF_UP))
                            .totalScore(recycle.add(kitchen.multiply(BigDecimal.valueOf(2))).add(carbon)
                                    .setScale(1, RoundingMode.HALF_UP))
                            .build();
                })
                .sorted(Comparator.comparing(DiyRankVO::getTotalScore).reversed())
                .toList();
        Long myAreaId = findMyAreaId(userId);
        Integer myAreaRank = null;
        if (myAreaId != null) {
            for (int i = 0; i < list.size(); i++) {
                if (Objects.equals(list.get(i).getAreaId(), myAreaId)) {
                    myAreaRank = i + 1;
                    break;
                }
            }
        }
        return DiyRankResultVO.builder().list(list).myAreaId(myAreaId).myAreaRank(myAreaRank).build();
    }

    private Integer currentLikes(Long postId) {
        DiyPost post = getById(postId);
        return post == null ? 0 : Math.max(0, post.getLikes());
    }

    private Map<Long, BigDecimal> toValueMap(List<DiyAreaAggVO> rows) {
        return rows.stream().collect(Collectors.toMap(DiyAreaAggVO::getAreaId,
                r -> r.getValue() == null ? BigDecimal.ZERO : r.getValue(), (a, b) -> a));
    }

    /** 用户无绑定片区：以最近业务单推断（回收单优先，垃圾投放上报兜底） */
    private Long findMyAreaId(Long userId) {
        Long areaId = baseMapper.findUserAreaFromRecycle(userId);
        if (areaId == null) {
            areaId = baseMapper.findUserAreaFromKitchen(userId);
        }
        return areaId;
    }
}

package com.dianfei.business.diy.controller;

import com.dianfei.business.diy.dto.DiyPostCreateDTO;
import com.dianfei.business.diy.entity.DiyPost;
import com.dianfei.business.diy.service.DiyPostService;
import com.dianfei.business.diy.vo.DiyLikeResultVO;
import com.dianfei.business.diy.vo.DiyPostVO;
import com.dianfei.business.diy.vo.DiyRankResultVO;
import com.dianfei.common.annotation.Idempotent;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 · 变废为宝 DIY 社区（LoopFill B6 创意 C2）：
 * 发布作品 / 作品列表（点赞倒序）/ 点赞幂等切换 / 片区环保榜
 */
@Tag(name = "变废为宝社区")
@RestController
@RequestMapping("/api/diy")
@RequiredArgsConstructor
public class DiyPostController {

    private final DiyPostService diyPostService;

    @Operation(summary = "发布变废为宝作品（标题 ≤64、内容 ≤500、图片选填 ≤300KB）")
    @Idempotent(prefix = "diy:post")
    @PostMapping("/posts")
    public Result<DiyPost> create(@Valid @RequestBody DiyPostCreateDTO dto) {
        return Result.success(diyPostService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "作品列表分页（点赞数倒序，回填昵称与已赞态）")
    @GetMapping("/posts")
    public Result<PageVO<DiyPostVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(diyPostService.page(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "点赞/取消点赞（幂等切换，返回最新 liked/likes）")
    @PostMapping("/posts/{id}/like")
    public Result<DiyLikeResultVO> like(@PathVariable Long id) {
        return Result.success(diyPostService.like(id, UserContext.getUserId()));
    }

    @Operation(summary = "片区环保榜（回收 kg + 垃圾投放袋数 + 碳减排 kg 三指标聚合 + 我的排名）")
    @GetMapping("/rank")
    public Result<DiyRankResultVO> rank() {
        return Result.success(diyPostService.rank(UserContext.getUserId()));
    }
}

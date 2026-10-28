package com.dianfei.business.flea.controller;

import com.dianfei.business.flea.dto.FleaItemCreateDTO;
import com.dianfei.business.flea.service.FleaItemService;
import com.dianfei.business.flea.vo.FleaItemVO;
import com.dianfei.common.annotation.Idempotent;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
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

/**
 * 后台 · 跳蚤市场（Upgrade U3）：闲置全量分页（含违规/下架/已售出）、违规下架；
 * 积分市场合并升级：管理员可发布实物（卖家=管理员账号，C2C 积分托管交易，含照片封面）。
 */
@Tag(name = "后台-跳蚤市场")
@RestController
@RequestMapping("/api/ops/flea")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class FleaOpsController {

    private final FleaItemService fleaItemService;

    @Operation(summary = "闲置商品全量分页（含下架/违规/已售出，keyword/userId 过滤）")
    @GetMapping("/items")
    public Result<PageVO<FleaItemVO>> items(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long userId) {
        return Result.success(fleaItemService.pageOps(keyword, userId, pageNum, pageSize));
    }

    @Operation(summary = "发布实物商品（卖家=当前管理员账号，C2C 积分交易，imageUrl 可选封面）")
    @Idempotent(prefix = "ops:flea:item")
    @OperLog(value = "发布实物", module = "跳蚤市场")
    @PostMapping("/items")
    public Result<FleaItemVO> create(@Valid @RequestBody FleaItemCreateDTO dto) {
        return Result.success(fleaItemService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "违规下架（status=3，通知卖家）")
    @OperLog(value = "违规下架", module = "跳蚤市场")
    @PutMapping("/items/{id}/forbidden")
    public Result<Void> forbidden(@PathVariable Long id) {
        fleaItemService.forbidden(id);
        return Result.success();
    }
}

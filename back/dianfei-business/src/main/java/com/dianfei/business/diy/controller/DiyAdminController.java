package com.dianfei.business.diy.controller;

import com.dianfei.business.diy.service.DiyPostService;
import com.dianfei.business.diy.vo.DiyPostVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.annotation.RequiresRole;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 · 变废为宝社区（n3）：作品分页管理 + 软删除
 */
@Tag(name = "管理端 - 变废为宝社区")
@RestController
@RequestMapping("/api/system/diy")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class DiyAdminController {

    private final DiyPostService diyPostService;

    @Operation(summary = "管理作品列表（创建时间倒序，keyword 匹配标题/内容/作者昵称/用户名）")
    @GetMapping("/posts")
    public Result<PageVO<DiyPostVO>> adminPage(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(diyPostService.adminPage(pageNum, pageSize, keyword));
    }

    @Operation(summary = "删除作品（软删除，社区列表同步消失）")
    @OperLog(value = "删除变废为宝作品", module = "变废为宝社区")
    @DeleteMapping("/posts/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        diyPostService.adminDelete(id);
        return Result.success();
    }
}

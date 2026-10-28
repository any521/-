package com.dianfei.business.aftersale.controller;

import com.dianfei.business.aftersale.dto.ComplaintCreateDTO;
import com.dianfei.business.aftersale.entity.Complaint;
import com.dianfei.business.aftersale.service.ComplaintService;
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
 * 用户端 · 售后（C4）：提交投诉/退款/追责/补发/仲裁，查询进度
 */
@Tag(name = "用户端-订单售后")
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @Operation(summary = "提交售后申请")
    @Idempotent(prefix = "complaint:submit")
    @PostMapping
    public Result<Complaint> submit(@Valid @RequestBody ComplaintCreateDTO dto) {
        return Result.success(complaintService.submit(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的售后单分页")
    @GetMapping
    public Result<PageVO<Complaint>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) Integer status) {
        return Result.success(complaintService.pageUser(UserContext.getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "售后单详情（限本人）")
    @GetMapping("/{id}")
    public Result<Complaint> detail(@PathVariable Long id) {
        return Result.success(complaintService.detail(id, UserContext.getUserId()));
    }
}

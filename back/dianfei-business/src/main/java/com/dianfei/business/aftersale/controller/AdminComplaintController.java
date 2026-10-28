package com.dianfei.business.aftersale.controller;

import com.dianfei.business.aftersale.dto.ComplaintHandleDTO;
import com.dianfei.business.aftersale.entity.Complaint;
import com.dianfei.business.aftersale.service.ComplaintService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 · 订单风控与售后（C4）：投诉处理、退款、追责、补发积分、纠纷仲裁
 */
@Tag(name = "后台-订单售后")
@RestController
@RequestMapping("/api/admin/complaints")
@RequiresRole("ADMIN")
@RequiredArgsConstructor
public class AdminComplaintController {

    private final ComplaintService complaintService;

    @Operation(summary = "售后单分页（状态/类型/来源单类型过滤，待处理优先）")
    @GetMapping
    public Result<PageVO<Complaint>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer type,
                                          @RequestParam(required = false) String orderType) {
        return Result.success(complaintService.pageAdmin(status, type, orderType, pageNum, pageSize));
    }

    @Operation(summary = "售后单详情")
    @GetMapping("/{id}")
    public Result<Complaint> detail(@PathVariable Long id) {
        return Result.success(complaintService.detail(id, null));
    }

    @Operation(summary = "处置售后单：受理（可补发积分/追责扣分，账本留痕）或驳回")
    @OperLog(value = "处置售后单", module = "订单售后")
    @PutMapping("/{id}/handle")
    public Result<Complaint> handle(@PathVariable Long id, @Valid @RequestBody ComplaintHandleDTO dto) {
        return Result.success(complaintService.handle(id, UserContext.getUserId(), dto));
    }
}

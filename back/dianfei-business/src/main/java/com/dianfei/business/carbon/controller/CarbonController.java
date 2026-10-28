package com.dianfei.business.carbon.controller;

import com.dianfei.business.carbon.dto.ScanPointsDTO;
import com.dianfei.business.carbon.entity.CarbonAccount;
import com.dianfei.business.carbon.entity.CarbonLog;
import com.dianfei.business.carbon.mapper.CarbonAccountMapper;
import com.dianfei.business.carbon.mapper.CarbonLogMapper;
import com.dianfei.business.carbon.service.CarbonQueryService;
import com.dianfei.business.carbon.service.CarbonVerifyService;
import com.dianfei.business.carbon.service.ScanPointsService;
import com.dianfei.business.carbon.vo.CarbonAccountVO;
import com.dianfei.business.carbon.vo.CarbonBillVO;
import com.dianfei.business.carbon.vo.CarbonCertificateVO;
import com.dianfei.business.carbon.vo.CarbonVerifyVO;
import com.dianfei.business.carbon.vo.ScanPointsVO;
import com.dianfei.common.annotation.OperLog;
import com.dianfei.common.exception.BusinessException;
import com.dianfei.common.result.PageVO;
import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import com.dianfei.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 · 碳积分（B2）：账户 / 流水 / 低碳账单 / 可信验证（12 章契约 /api/carbon/*）
 */
@Tag(name = "碳积分账户")
@RestController
@RequestMapping("/api/carbon")
@RequiredArgsConstructor
public class CarbonController {

    private final CarbonQueryService queryService;
    private final CarbonVerifyService verifyService;
    private final ScanPointsService scanPointsService;
    private final CarbonLogMapper logMapper;
    private final CarbonAccountMapper accountMapper;

    @Operation(summary = "我的碳积分账户（余额/冻结/累计减排）")
    @GetMapping("/account")
    public Result<CarbonAccountVO> account() {
        return Result.success(queryService.account(UserContext.getUserId()));
    }

    @Operation(summary = "积分流水分页（含哈希链字段）")
    @GetMapping("/logs")
    public Result<PageVO<CarbonLog>> logs(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(queryService.logs(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "低碳账单（按月聚合，month=yyyy-MM 可选，缺省查当月）")
    @GetMapping("/bill")
    public Result<CarbonBillVO> bill(@RequestParam(required = false) String month) {
        return Result.success(queryService.bill(UserContext.getUserId(), month));
    }

    @Operation(summary = "F3 流水可信验证：哈希链校验 + Merkle 路径到锚定根（公开）")
    @GetMapping("/verify/{logId}")
    public Result<CarbonVerifyVO> verify(@PathVariable Long logId) {
        return Result.success(verifyService.verify(logId));
    }

    @Operation(summary = "碳减排可信证书（LoopFill B4）：业务摘要 + 哈希链 + Merkle 锚定校验，限本人")
    @GetMapping("/certificate/{logId}")
    public Result<CarbonCertificateVO> certificate(@PathVariable Long logId) {
        CarbonLog row = logMapper.selectById(logId);
        if (row == null) {
            throw new BusinessException("流水不存在");
        }
        // 归属校验：流水账户必须属于当前用户（非本人 403）
        CarbonAccount account = accountMapper.selectById(row.getAccountId());
        if (account == null || !account.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该流水");
        }
        CarbonVerifyVO v = verifyService.verify(logId);
        return Result.success(CarbonCertificateVO.builder()
                .logId(row.getId())
                .bizType(row.getOrderType())
                .remark(row.getRemark())
                .points(row.getPoints())
                .carbonReduced(row.getCarbonReduced())
                .hashValid(v.isHashValid())
                .chainLinked(v.isChainLinked())
                .anchored(v.isAnchored())
                .anchorDate(v.getAnchorDate())
                .merkleRoot(v.getMerkleRoot())
                .rootMatched(v.getRootMatched())
                .proof(v.getProof())
                .valid(v.isValid())
                .createTime(row.getCreateTime())
                .build());
    }

    @Operation(summary = "CAM 扫描识别积分发放（日频控，超限不报错）")
    @OperLog(value = "扫描识别积分发放", module = "用户端-回收")
    @PostMapping("/scan-points")
    public Result<ScanPointsVO> scanPoints(@RequestBody ScanPointsDTO dto) {
        return Result.success(scanPointsService.award(UserContext.getUserId(), dto));
    }
}

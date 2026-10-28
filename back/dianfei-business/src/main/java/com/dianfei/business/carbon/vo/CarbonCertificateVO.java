package com.dianfei.business.carbon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 碳减排可信证书（LoopFill B4，GET /api/carbon/certificate/{logId}）：
 * 业务摘要（CarbonLog）+ 哈希链校验 + Merkle 路径到每日锚定根（CarbonVerifyVO 同源字段）
 */
@Data
@Builder
@Schema(description = "碳减排可信证书：业务摘要 + 哈希链 + Merkle 锚定校验")
public class CarbonCertificateVO {

    @Schema(description = "流水 ID")
    private Long logId;

    /** RECYCLE/KITCHEN/REPAIR/MALL */
    @Schema(description = "业务类型（RECYCLE/KITCHEN/REPAIR/MALL）")
    private String bizType;

    @Schema(description = "备注（规则命中/调整原因等）")
    private String remark;

    @Schema(description = "积分变动（正=入账 负=出账）")
    private Long points;

    @Schema(description = "本笔减排量 kgCO2")
    private BigDecimal carbonReduced;

    @Schema(description = "payload 重算哈希与 curr_hash 一致（未被篡改）")
    private boolean hashValid;

    @Schema(description = "prev_hash 与账户链上前一条 curr_hash 衔接")
    private boolean chainLinked;

    @Schema(description = "是否已进入每日锚定")
    private boolean anchored;

    @Schema(description = "锚定日期")
    private LocalDate anchorDate;

    @Schema(description = "锚定 Merkle 根")
    private String merkleRoot;

    @Schema(description = "按验证路径重算根与锚定根一致")
    private Boolean rootMatched;

    @Schema(description = "Merkle 验证路径（独立第三方可复核）")
    private List<CarbonVerifyVO.ProofNodeVO> proof;

    @Schema(description = "综合结论：链完整且（已锚定时）可验证到根")
    private boolean valid;

    @Schema(description = "流水创建时间")
    private LocalDateTime createTime;
}

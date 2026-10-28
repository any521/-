package com.dianfei.business.carbon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 减排流水可信验证结果（GET /api/carbon/verify/{logId}，F3）：
 * 哈希链校验 + Merkle 验证路径到每日锚定根
 */
@Data
@Builder
@Schema(description = "碳流水可信验证结果")
public class CarbonVerifyVO {

    @Schema(description = "流水 ID")
    private Long logId;

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
    private List<ProofNodeVO> proof;

    @Schema(description = "综合结论：链完整且（已锚定时）可验证到根")
    private boolean valid;

    @Data
    @Builder
    @Schema(description = "Merkle 验证路径节点")
    public static class ProofNodeVO {

        @Schema(description = "兄弟节点哈希")
        private String hash;

        @Schema(description = "兄弟是否在左侧")
        private boolean left;
    }
}

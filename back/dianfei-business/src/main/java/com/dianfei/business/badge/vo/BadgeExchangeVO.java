package com.dianfei.business.badge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 徽章兑换记录 VO（积分市场合并升级）：balanceAfter 仅兑换动作实时返回，列表查询为 null。
 */
@Data
@Schema(description = "徽章兑换记录")
public class BadgeExchangeVO {

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "兑换单号（BE 前缀）")
    private String exchangeNo;

    @Schema(description = "徽章 ID")
    private Long badgeId;

    @Schema(description = "徽章名快照")
    private String badgeName;

    @Schema(description = "徽章编码快照（前端匹配内置 SVG 图标）")
    private String badgeCode;

    @Schema(description = "徽章图标快照")
    private String badgeIcon;

    @Schema(description = "本单扣减积分")
    private Long pointsCost;

    @Schema(description = "0已兑换（虚拟商品即到账）")
    private Integer status;

    @Schema(description = "动作后积分余额（仅兑换动作返回）")
    private Long balanceAfter;

    @Schema(description = "兑换时间")
    private LocalDateTime createTime;
}

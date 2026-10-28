package com.dianfei.business.badge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 徽章 VO（积分市场合并升级）：列表/详情共用；
 * owned 仅用户端列表在登录态下回填（当前用户是否已兑换该徽章）。
 */
@Data
@Builder
@Schema(description = "成就徽章")
public class BadgeVO {

    @Schema(description = "徽章 ID")
    private Long id;

    @Schema(description = "徽章编码")
    private String code;

    @Schema(description = "徽章名称")
    private String name;

    @Schema(description = "徽章描述")
    private String description;

    @Schema(description = "兑换所需积分")
    private Long price;

    @Schema(description = "徽章图标/封面")
    private String iconUrl;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "0下架 1上架")
    private Integer status;

    @Schema(description = "当前用户是否已兑换（仅用户端登录态回填）")
    private Boolean owned;
}

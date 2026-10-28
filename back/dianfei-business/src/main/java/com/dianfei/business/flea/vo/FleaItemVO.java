package com.dianfei.business.flea.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 闲置商品 VO（Upgrade U3）：列表/详情共用；
 * 列表回填卖家昵称/头像 + soldCount；详情追加 sellerStats（在售/已售出统计）。
 */
@Data
@Builder
public class FleaItemVO {

    private Long id;

    private Long userId;

    private String sellerNickname;

    private String sellerAvatar;

    private String title;

    private String description;

    /** REUSABLE/TOY/BOOK/ELECTRONIC/HOME/OTHER */
    private String category;

    private Long price;

    private String imageUrl;

    /** 0下架 1在售 2已售出 3违规下架 */
    private Integer status;

    private String statusDesc;

    /** 该卖家已售出数（简单聚合） */
    private Long soldCount;

    /** 详情页卖家统计（在售/已售出） */
    private FleaStatsVO sellerStats;

    private LocalDateTime createTime;
}

package com.dianfei.business.diy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 变废为宝 DIY 作品（LoopFill B6，biz_diy_post）：
 * 用户发布废旧物改造作品供社区互相点赞学习；likes 为冗余计数（列表排序用），
 * 由 DiyPostService.like 条件更新维护，点赞明细存 biz_diy_like（uk 防重复）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_diy_post")
public class DiyPost extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String title;

    private String content;

    /** 作品图（base64 压缩 ≤300KB 或 MinIO URL，选填） */
    private String imageUrl;

    /** 点赞数（冗余计数，列表排序用） */
    private Integer likes;
}

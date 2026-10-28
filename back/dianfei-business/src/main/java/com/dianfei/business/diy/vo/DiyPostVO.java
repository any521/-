package com.dianfei.business.diy.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DIY 作品列表行（LoopFill B6）：回填作者昵称 + 当前用户已赞态
 */
@Data
@Builder
public class DiyPostVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 作者昵称（拼 sys_user，查不到兜底「环保达人」） */
    private String nickname;

    private String title;

    private String content;

    private String imageUrl;

    private Integer likes;

    /** 当前用户是否已赞（列表批量查 biz_diy_like） */
    private Boolean likedByMe;

    private LocalDateTime createTime;
}

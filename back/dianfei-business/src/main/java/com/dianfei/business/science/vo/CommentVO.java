package com.dianfei.business.science.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章评论 VO（V50）
 */
@Data
@Builder
public class CommentVO {

    private String id;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private UserBrief user;

    @Data
    @Builder
    public static class UserBrief {
        private String nickname;
        private String avatar;
    }
}

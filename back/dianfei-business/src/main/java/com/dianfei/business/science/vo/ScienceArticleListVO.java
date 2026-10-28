package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文章列表 VO（V42）
 */
@Data
@Builder
public class ScienceArticleListVO {
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private String authorName;
    private Boolean isSystem;
    private Boolean allowDownload;
    private Integer downloadPoints;
    private Integer isTop;
    private Integer viewCount;
    private Integer downloadCount;
    private Integer likeCount;
    private Integer status; // 0=待审，1=已发布，2=已下架，3=被驳回
    private String categories; // V42: 分类名称（从 categories JSON 中解析）
    private String publishTime;
}

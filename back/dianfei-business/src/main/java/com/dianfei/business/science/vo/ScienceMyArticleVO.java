package com.dianfei.business.science.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 我的文章 VO（V42）
 */
@Data
@Builder
public class ScienceMyArticleVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private String authorName;
    private Integer status;
    private Boolean allowDownload;
    private Integer downloadPoints;
    private Integer viewCount;
    private Integer downloadCount;
    private Integer likeCount;
    private String publishTime;
    private String auditRemark;
    private IncomeInfoVO income;
}

package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 概览数据统计 VO（V42）
 */
@Data
@Builder
public class OverviewVO {
    private Integer totalArticles;
    private Integer systemArticles;
    private Integer userArticles;
    private Integer pendingAudit;
    private Integer publishedArticles;
    private Integer totalViews;
    private Integer totalDownloads;
    private Integer totalAuthors;
}

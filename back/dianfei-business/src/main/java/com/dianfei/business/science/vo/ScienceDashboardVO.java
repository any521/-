package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 统计看板 VO（V42）
 */
@Data
@Builder
public class ScienceDashboardVO {
    /** 概览数据 */
    private OverviewVO overview;
    /** 趋势数据 */
    private TrendsVO trends;
    /** 热门文章 */
    private java.util.List<ScienceArticleListVO> topArticles;
    /** 热门作者 */
    private java.util.List<TopAuthorVO> topAuthors;
}

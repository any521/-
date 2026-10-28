package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 我的科普文章统计 VO（V51）
 */
@Data
@Builder
public class MyStatsVO {

    /** 总文章数 */
    private Integer totalArticles;

    /** 总阅读量 */
    private Integer totalViews;

    /** 总下载次数 */
    private Integer totalDownloads;

    /** 预计收益（积分）= Σ(下载次数 × 基础单价 × 热门系数) */
    private Double estimatedIncome;
}

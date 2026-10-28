package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 趋势数据 VO（V42）
 */
@Data
@Builder
public class TrendsVO {
    private List<Integer> dailyArticles;
    private List<Integer> dailyViews;
    private List<Integer> dailyDownloads;
}

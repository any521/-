package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文章下载结果 VO（V42）
 */
@Data
@Builder
public class ScienceDownloadResult {
    private String downloadToken;
    private Integer expiresIn;
    private Integer pointsCost;
    private Integer balanceAfter;
}

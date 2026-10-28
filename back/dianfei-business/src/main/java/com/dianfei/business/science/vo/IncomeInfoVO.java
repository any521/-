package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 收益信息 VO（V42）
 */
@Data
@Builder
public class IncomeInfoVO {
    private Integer totalDownloads;
    private Integer totalPoints;
    private String lastIncomeTime;
}

package com.dianfei.business.science.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 热门作者 VO（V42）
 */
@Data
@Builder
public class TopAuthorVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String nickname;
    private Integer articleCount;
    private Integer totalIncome;
    private Double avgRating;
}

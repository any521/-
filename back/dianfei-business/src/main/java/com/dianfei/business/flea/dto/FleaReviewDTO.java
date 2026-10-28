package com.dianfei.business.flea.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 跳蚤订单评价（Upgrade U3）：rating 1-5 必填，comment ≤200；仅买家、已完成、未评过可评一次。
 */
@Data
public class FleaReviewDTO {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分 1-5")
    @Max(value = 5, message = "评分 1-5")
    private Integer rating;

    @Size(max = 200, message = "评价最长 200 字")
    private String comment;
}

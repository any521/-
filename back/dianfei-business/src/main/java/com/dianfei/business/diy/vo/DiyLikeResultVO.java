package com.dianfei.business.diy.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 点赞/取消点赞结果（LoopFill B6）：返回最新已赞态与点赞数
 */
@Data
@Builder
public class DiyLikeResultVO {

    /** true 已赞 / false 已取消 */
    private Boolean liked;

    private Integer likes;
}

package com.dianfei.business.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端首页摘要（GET /api/home/summary）：积分/累计减排/本月减排/最新公告
 */
@Data
@Builder
@Schema(description = "首页摘要")
public class HomeSummaryVO {

    @Schema(description = "可用积分")
    private Long points;

    @Schema(description = "累计减排量 kgCO2")
    private BigDecimal totalCarbon;

    @Schema(description = "本月减排量 kgCO2")
    private BigDecimal monthCarbon;

    @Schema(description = "最新已发布公告标题，无则空串")
    private String notice;

    @Schema(description = "最新公告发布时间（前端按发布身份记忆关闭），无公告为 null")
    private LocalDateTime noticePublishTime;
}

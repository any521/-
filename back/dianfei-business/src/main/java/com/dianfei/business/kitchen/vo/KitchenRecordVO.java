package com.dianfei.business.kitchen.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 垃圾投放记录分页项（G5，GET /api/admin/kitchen/records；员工端审核列表复用）：
 * 联 biz_area.area_name、sys_user.nickname；aiAuditResult 由 status 映射文案，
 * points 按袋数 × KITCHEN/DEFAULT 规则每袋积分测算（规则缺失为 0）。
 */
@Data
@Schema(description = "垃圾投放记录分页项")
public class KitchenRecordVO {

    @Schema(description = "上报记录 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "片区名称")
    private String areaName;

    @Schema(description = "用户昵称（无昵称回退用户名）")
    private String userName;

    @Schema(description = "袋数")
    private Integer bags;

    @Schema(description = "投放照片（MinIO）")
    private String photoUrl;

    @Schema(description = "AI 审核结果：自动通过/待人审/驳回")
    private String aiAuditResult;

    @Schema(description = "应得积分 = 袋数 × 每袋积分（规则缺失为 0）")
    private Integer points;

    @Schema(description = "投放时间 yyyy-MM-dd HH:mm:ss")
    private String createTime;
}

package com.dianfei.business.science.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 科普文章主表（V42）：用户生成内容（UGC）+ 系统文章的科普知识平台
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_science_article")
public class ScienceArticle extends BaseEntity {

    // 基础信息
    private String title;
    private String description;
    private String content;
    private String coverUrl;

    // PDF 文件信息
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pdfFileId;
    private String pdfFilename;
    private String pdfStoragePath;

    // 内容与分类
    private String categories;  // JSON: [{"code":"classification","name":"垃圾分类"}]
    private String tags;

    // 作者信息
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;

    // 状态与权限
    private Integer isSystem;  // 0=用户文章，1=系统文章
    private Integer allowDownload;  // 0=禁止，1=允许
    private Integer status;  // 0=待审，1=已发布，2=已下架，3=被驳回
    private Integer isTop;

    // 积分配置
    private Integer downloadPoints;
    private BigDecimal basePoints;
    private BigDecimal multiplier;

    // 统计指标
    private Integer viewCount;
    private Integer downloadCount;
    private Integer likeCount;

    // 审核信息
    private String auditRemark;
    private String auditTime;
    private String auditBy;

    // 发布时间（发布操作时写入，与 create_time 解耦）
    private java.time.LocalDateTime publishTime;
}

package com.dianfei.business.science.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 文章详情 VO（V42）
 */
@Data
@Builder
public class ScienceArticleDetailVO {

    private Long id;
    private String title;
    private String description;
    private String content;
    private String coverUrl;
    private String pdfFilename;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pdfFileId;
    private String pdfStoragePath;
    
    /** categories 以 JSON 字符串存储，避免类重复定义 (简化实现) */
    private String categories;  // [{"code":"classification","name":"垃圾分类"}]
    private String tags;
    
    private AuthorInfoVO author;
    
    private Boolean isSystem;
    private Boolean allowDownload;
    private Integer status;
    private Integer isTop;
    
    /** 单次下载所需积分 */
    private Integer downloadPoints;
    private BigDecimal basePoints;
    private BigDecimal multiplier;
    
    private Integer viewCount;
    private Integer downloadCount;
    private Integer likeCount;
    
    private RatingInfoVO rating;
    
    private String publishTime;
}

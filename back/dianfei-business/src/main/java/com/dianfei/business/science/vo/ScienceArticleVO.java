package com.dianfei.business.science.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 文章列表/编辑/发布返回 VO（V42）
 */
@Data
@Builder
public class ScienceArticleVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    private String title;
    private String description;
    private String coverUrl;
    
    private Boolean isSystem;
    private Integer status;
    private Boolean allowDownload;
    private Integer downloadPoints;
    private Integer viewCount;
    private Integer downloadCount;
    
    private String publishTime;
}

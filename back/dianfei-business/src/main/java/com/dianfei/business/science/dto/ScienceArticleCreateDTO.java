package com.dianfei.business.science.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建/编辑文章 DTO（V42）
 */
@Data
public class ScienceArticleCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200 字符")
    private String title;

    @Size(max = 1000, message = "简介长度不能超过 1000 字符")
    private String description;

    /** PDF 文件 ID（已上传后返回） */
    private Long pdfFileId;
    
    private String pdfFilename;
    
    private String pdfStoragePath;

    /** 封面图片 URL（已上传后返回） */
    private String coverUrl;

    /** 分类标签数组 [{"code": "垃圾分类", "name": "垃圾分类"}] */
    private List<ScienceCategoryDTO> categories;

    /** 扩展标签逗号分隔 */
    @Size(max = 500, message = "标签长度不能超过 500 字符")
    private String tags;

    /** 作者昵称（前端传递，冗余字段） */
    @NotBlank(message = "作者昵称不能为空")
    @Size(max = 64, message = "作者昵称不超过 64 字符")
    private String authorName;

    /** 是否允许下载：0=禁止，1=允许 */
    private Integer allowDownload;

    /** 单次下载所需积分（允许下载时必须填写，范围 1-100；0 表示免费） */
    private Integer downloadPoints;

    /** 置顶排序（数字越大越靠前，管理员配置） */
    private Integer isTop;

    /** 基础积分单价（管理员后台配置） */
    private Double basePoints;

    /** 热门系数（管理员后台配置） */
    private Double multiplier;
}

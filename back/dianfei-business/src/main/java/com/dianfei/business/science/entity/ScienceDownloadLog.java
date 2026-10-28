package com.dianfei.business.science.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 下载积分记录表（V42）
 */
@Data
@TableName("biz_science_download_log")
public class ScienceDownloadLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long articleId;
    private Long userId;
    private Integer pointsCost;
    private String downloadToken;  // Redis 一次性消费凭据
}

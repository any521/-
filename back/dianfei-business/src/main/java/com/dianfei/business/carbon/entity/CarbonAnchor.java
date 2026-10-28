package com.dianfei.business.carbon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日 Merkle 锚定（B2，10.5，仅 INSERT）：当日全部流水 curr_hash 为叶子构树
 * 注：表结构无 BaseEntity 公共字段，独立映射
 */
@Data
@TableName("biz_carbon_anchor")
public class CarbonAnchor implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate anchorDate;

    private String merkleRoot;

    private Integer leafCount;

    /** 外部存证凭证 */
    private String externalRef;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {

    private String title;

    private String content;

    /** 1 公告 / 2 通知 */
    private Integer noticeType;

    /** 目标端：ALL / USER / STAFF / ADMIN */
    private String target;

    /** 0 下线 / 1 发布 */
    private Integer status;

    private LocalDateTime publishTime;
}

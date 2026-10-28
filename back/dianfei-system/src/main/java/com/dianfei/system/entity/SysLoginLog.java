package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志（永久归档 D7，无逻辑删除）
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** USER / STAFF / ADMIN */
    private String clientType;

    private String loginIp;

    private String userAgent;

    /** 0 成功 / 1 失败 */
    private Integer status;

    /** 失败原因 */
    private String msg;

    private LocalDateTime loginTime;
}

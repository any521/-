package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志（切面异步落库，永久归档 D7，无逻辑删除）
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;

    private String operation;

    private String method;

    private String params;

    private String result;

    /** 0 成功 / 1 失败 */
    private Integer status;

    private String errorMsg;

    private Long costMs;

    private Long operId;

    private String operName;

    /** 操作者主体类型：HUMAN / AGENT / MCP */
    private String principalType;

    private String operIp;

    private LocalDateTime operTime;
}

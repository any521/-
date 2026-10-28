package com.dianfei.common.log;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志记录（切面产出，落库由 system 模块的 OperLogStorage 实现）
 */
@Data
@Builder
public class OperLogRecord {

    private String module;
    private String operation;
    private String method;
    private String params;
    private String result;
    /** 0 成功 / 1 失败 */
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private String operName;
    private Long operId;
    /** 操作者主体类型：HUMAN / AGENT / MCP */
    private String principalType;
    private String operIp;
    private LocalDateTime operTime;
}

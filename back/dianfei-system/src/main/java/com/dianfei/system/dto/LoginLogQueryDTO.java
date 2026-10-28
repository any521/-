package com.dianfei.system.dto;

import com.dianfei.common.result.PageDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志分页查询入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登录日志分页查询参数")
public class LoginLogQueryDTO extends PageDTO {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "客户端：USER / STAFF / ADMIN")
    private String clientType;

    @Schema(description = "0 成功 / 1 失败")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}

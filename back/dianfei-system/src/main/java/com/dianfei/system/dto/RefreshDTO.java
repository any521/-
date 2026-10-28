package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "刷新令牌入参")
public class RefreshDTO {

    /** 兼容非浏览器客户端；浏览器使用 HttpOnly Cookie，不在请求体传 refresh token。 */
    private String refreshToken;
}

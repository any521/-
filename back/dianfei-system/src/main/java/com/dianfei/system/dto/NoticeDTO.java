package com.dianfei.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 公告新增/编辑入参
 */
@Data
@Schema(description = "公告新增/编辑参数")
public class NoticeDTO {

    @Schema(description = "公告 id，编辑时必传")
    private Long id;

    @Schema(description = "标题")
    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "1 公告 / 2 通知")
    private Integer noticeType = 1;

    @Schema(description = "目标端：ALL / USER / STAFF / ADMIN")
    @Pattern(regexp = "^(ALL|USER|STAFF|ADMIN)$", message = "目标端须为 ALL/USER/STAFF/ADMIN")
    private String target = "ALL";

    @Schema(description = "0 下线 / 1 发布")
    private Integer status = 1;
}

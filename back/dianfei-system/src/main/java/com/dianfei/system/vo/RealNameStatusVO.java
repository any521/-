package com.dianfei.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 实名认证状态（GapFill B1，V1）：用户端 realname.vue 状态机数据源
 */
@Data
@Builder
@Schema(description = "实名认证状态")
public class RealNameStatusVO {

    /** 0未认证 1待审 2已通过 3已驳回 */
    private Integer status;

    /** 审核备注（驳回原因） */
    private String remark;

    /** 实名姓名（已通过时展示脱敏） */
    private String realName;
}

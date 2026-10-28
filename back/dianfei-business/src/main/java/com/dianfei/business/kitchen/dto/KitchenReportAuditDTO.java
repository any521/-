package com.dianfei.business.kitchen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工端垃圾投放审核入参（B4）：通过即发分（账本幂等）；驳回需原因透传用户消息
 */
@Data
public class KitchenReportAuditDTO {

    /** true 通过（发分）/ false 驳回（不发分） */
    @NotNull(message = "审核结果不能为空")
    private Boolean pass;

    /** 驳回原因（驳回时建议填写，将随通知透传用户） */
    private String remark;
}

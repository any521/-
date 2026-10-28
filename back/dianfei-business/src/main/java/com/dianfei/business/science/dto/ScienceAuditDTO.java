package com.dianfei.business.science.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核请求 DTO（V42）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScienceAuditDTO {
    private Boolean pass;
    private String remark;
}

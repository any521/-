package com.dianfei.ai.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** F2 预诊请求（对齐 ai-back schemas.repair.RepairDiagnoseRequest） */
@Data
public class RepairDiagnoseDTO {

    /** 维修工单 ID（可空：下单前预诊） */
    private Long orderId;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    private String brand;

    @NotBlank(message = "故障描述不能为空")
    @Size(min = 2, max = 500, message = "故障描述 2-500 字")
    private String faultDesc;

    /** 故障照片（可选） */
    private List<String> imageUrls;
}

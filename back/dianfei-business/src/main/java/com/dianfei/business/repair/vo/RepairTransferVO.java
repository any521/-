package com.dianfei.business.repair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 转回收联动结果（C2）：维修单与新生回收单的双向关联快照
 */
@Data
@Schema(description = "维修转回收联动结果")
public class RepairTransferVO {

    @Schema(description = "维修工单 ID")
    private String repairOrderId;

    @Schema(description = "维修工单号")
    private String repairOrderNo;

    @Schema(description = "维修单状态（5=已转回收）")
    private Integer repairStatus;

    @Schema(description = "联动生成的回收工单 ID")
    private String recycleOrderId;

    @Schema(description = "回收工单号")
    private String recycleOrderNo;

    @Schema(description = "回收单状态（0=待接单）")
    private Integer recycleStatus;
}

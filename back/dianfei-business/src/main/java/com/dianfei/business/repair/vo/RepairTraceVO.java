package com.dianfei.business.repair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 双单溯源视图（C2 归档）：维修单 + 联动回收单双向关联，永久留存
 */
@Data
@Schema(description = "维修-回收双单溯源")
public class RepairTraceVO {

    @Schema(description = "维修工单 ID")
    private String repairOrderId;

    @Schema(description = "维修工单号")
    private String repairOrderNo;

    @Schema(description = "维修单状态")
    private Integer repairStatus;

    @Schema(description = "维修单状态描述")
    private String repairStatusDesc;

    @Schema(description = "可修复性判定 0未判定 1可修复 2不可修复")
    private Integer repairable;

    @Schema(description = "故障类型")
    private String faultType;

    @Schema(description = "维修单创建时间")
    private LocalDateTime repairCreateTime;

    @Schema(description = "维修单完结时间")
    private LocalDateTime repairFinishTime;

    @Schema(description = "联动回收工单 ID（未联动为空）")
    private String recycleOrderId;

    @Schema(description = "回收工单号")
    private String recycleOrderNo;

    @Schema(description = "回收单状态")
    private Integer recycleStatus;

    @Schema(description = "回收单创建时间")
    private LocalDateTime recycleCreateTime;

    @Schema(description = "双向关联校验：recycle.repair_ref 是否指回本维修单")
    private Boolean bidirectional;
}

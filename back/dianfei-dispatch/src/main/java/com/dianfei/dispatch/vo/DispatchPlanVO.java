package com.dianfei.dispatch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * F4 派单方案 VO：按片区→员工路线→停靠点三级展开，供后台预览/大屏渲染。
 */
@Data
@Schema(description = "VRPTW 派单方案")
public class DispatchPlanVO {

    private LocalDate date;

    @Schema(description = "求解器：TIMEFOLD_VRPTW / DISTANCE_FALLBACK（降级）")
    private String solver;

    @Schema(description = "true=本次走了降级链")
    private Boolean degraded;

    @Schema(description = "当日待派单总数")
    private Integer totalPending;

    @Schema(description = "本方案覆盖单数")
    private Integer totalPlanned;

    @Schema(description = "true=已直接指派落库")
    private Boolean applied;

    private List<AreaPlan> areas = new ArrayList<>();

    @Data
    @Schema(description = "单片区方案")
    public static class AreaPlan {

        private Long areaId;

        @Schema(description = "无在岗回收员等跳过原因，正常为 null")
        private String skipReason;

        private List<Route> routes = new ArrayList<>();
    }

    @Data
    @Schema(description = "单员工路线")
    public static class Route {

        private Long staffId;

        private String staffName;

        @Schema(description = "路线总载重 kg（预估）")
        private BigDecimal totalKg;

        @Schema(description = "路线总里程 km")
        private BigDecimal totalKm;

        private List<Stop> stops = new ArrayList<>();
    }

    @Data
    @Schema(description = "停靠点（按上门顺序）")
    public static class Stop {

        @Schema(description = "顺路序号，从 1 起")
        private Integer seq;

        private String orderId;

        private String orderNo;

        private String address;

        private LocalDateTime appointTime;

        @Schema(description = "预计到达 HH:mm（含行驶+服务时长推演）")
        private String eta;

        private BigDecimal estWeightKg;
    }
}

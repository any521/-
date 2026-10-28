package com.dianfei.business.diy.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 片区环保榜整体返回（LoopFill B6）：榜单 + 我的片区排名
 */
@Data
@Builder
public class DiyRankResultVO {

    /** 榜单（totalScore 降序，仅含启用片区且有环保数据的片区） */
    private List<DiyRankVO> list;

    /** 我的片区（最近业务单推断；无业务记录为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long myAreaId;

    /** 我的片区排名（1 起；片区无环保数据未上榜为 null） */
    private Integer myAreaRank;
}

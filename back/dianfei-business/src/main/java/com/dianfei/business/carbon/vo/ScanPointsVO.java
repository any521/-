package com.dianfei.business.carbon.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 扫描识别积分发放出参（CAM cam04，契约 C6-9）：命中日上限时 points=0 + hitLimit=true，
 * 不报错不阻断（C6-5 静默降级）。
 */
@Data
@Builder
public class ScanPointsVO {

    /** 本次发放积分（超限为 0） */
    private long points;

    /** 当日累计发放次数（含本次） */
    private long dailyTotal;

    /** 日上限（sys_config carbon.scan.daily_limit） */
    private long dailyLimit;

    /** 是否命中日上限（前端静默处理，不提示） */
    private boolean hitLimit;
}

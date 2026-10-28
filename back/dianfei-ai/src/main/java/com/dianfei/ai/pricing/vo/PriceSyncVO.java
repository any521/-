package com.dianfei.ai.pricing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手动联网同步报价结果（POST /api/ai/market-price/sync）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceSyncVO {

    /** 定位城市（AMap IP 定位，失败回退配置默认） */
    private String city;

    /** 成功更新的品类数 */
    private int updated;

    /** 跳过品类数（无结果/降级被丢弃/涨跌幅超护栏） */
    private int skipped;

    /** 同步是否已提交执行（false=已有同步进行中，本次未提交；异步化后立即返回） */
    private boolean started;
}

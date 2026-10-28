package com.dianfei.business.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 兑换单出参（C3）：balanceAfter 仅兑换/取消动作实时返回，列表查询为 null
 */
@Data
@Schema(description = "积分兑换单")
public class MallExchangeVO {

    @Schema(description = "兑换单 ID")
    private String id;

    @Schema(description = "兑换单号（MO 前缀）")
    private String exchangeNo;

    @Schema(description = "商品 ID")
    private String productId;

    @Schema(description = "商品名快照")
    private String productName;

    @Schema(description = "本单扣减积分总额")
    private Long pointsCost;

    @Schema(description = "兑换数量")
    private Integer quantity;

    @Schema(description = "关联扣分流水 ID（溯源）")
    private String carbonLogId;

    @Schema(description = "0已兑换 1已发货 2已完成 3已取消")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "收货地址")
    private String address;

    @Schema(description = "动作后积分余额（仅兑换/取消动作返回）")
    private Long balanceAfter;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

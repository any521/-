package com.dianfei.business.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品保存（新增/编辑，后台 C3）
 */
@Data
@Schema(description = "商城商品保存")
public class MallProductSaveDTO {

    @Schema(description = "商品 ID（编辑时必填）")
    private Long id;

    @NotBlank(message = "商品名不能为空")
    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品图 URL（MinIO）")
    private String coverUrl;

    @NotNull(message = "所需积分不能为空")
    @Min(value = 1, message = "所需积分必须为正数")
    @Schema(description = "所需积分")
    private Long pointsPrice;

    @Min(value = 0, message = "库存不能为负")
    @Schema(description = "库存（新增时必填）")
    private Integer stock;

    @Schema(description = "0下架 1上架（默认 0）")
    private Integer status;

    @Schema(description = "商品描述")
    private String description;
}

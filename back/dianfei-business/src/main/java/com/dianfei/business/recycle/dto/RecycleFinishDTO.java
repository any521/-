package com.dianfei.business.recycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工完成登记入参（B3）：实收明细（逐品类称重）+ 转运去向，
 * 服务端按 biz_recycle_price 逐条核算金额并汇总
 */
@Data
public class RecycleFinishDTO {

    /** 实收明细，至少一条 */
    @Valid
    @NotEmpty(message = "实收明细不能为空")
    private List<ItemDTO> items;

    /** 转运去向（溯源），如"XX 分拣中心" */
    @NotBlank(message = "转运去向不能为空")
    private String transferTo;

    @Data
    public static class ItemDTO {

        @NotBlank(message = "明细品类不能为空")
        private String category;

        @NotNull(message = "明细重量不能为空")
        private BigDecimal weight;
    }
}

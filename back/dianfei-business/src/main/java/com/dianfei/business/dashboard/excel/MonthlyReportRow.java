package com.dianfei.business.dashboard.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度报表导出行（C5 EasyExcel）：与 MonthlyReportVO 同口径
 */
@Data
@ColumnWidth(18)
public class MonthlyReportRow {

    @ExcelProperty("月份")
    private String month;

    @ExcelProperty("完成回收单数")
    private Long recycleOrders;

    @ExcelProperty("回收重量(kg)")
    private BigDecimal recycleWeight;

    @ExcelProperty("垃圾投放袋数")
    private Long kitchenBags;

    @ExcelProperty("维修单数")
    private Long repairOrders;

    @ExcelProperty("碳减排(kgCO2)")
    private BigDecimal carbonReduced;
}

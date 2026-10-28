package com.dianfei.business.dashboard.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回收台账导出行（C5 EasyExcel，环保台账备查 · D7 永久归档口径）
 */
@Data
@ColumnWidth(18)
public class RecycleLedgerRow {

    @ExcelProperty("订单号")
    @ColumnWidth(24)
    private String orderNo;

    @ExcelProperty("片区")
    private String areaName;

    @ExcelProperty("品类")
    private String category;

    @ExcelProperty("实收重量(kg)")
    private BigDecimal weight;

    @ExcelProperty("回款金额(元)")
    private BigDecimal amount;

    @ExcelProperty("状态")
    private String statusText;

    @ExcelProperty("完成时间")
    @ColumnWidth(22)
    private LocalDateTime finishTime;
}

package com.dianfei.business.dashboard.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.dianfei.business.dashboard.excel.MonthlyReportRow;
import com.dianfei.business.dashboard.excel.RecycleLedgerRow;
import com.dianfei.business.dashboard.mapper.DashboardMapper;
import com.dianfei.business.dashboard.vo.MonthlyReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;

/**
 * 台账/月报 EasyExcel 导出（C5）：Sheet1 回收台账（D7 永久归档口径）+ Sheet2 近 12 个月月报
 */
@Service
@RequiredArgsConstructor
public class DashboardExportService {

    private final DashboardMapper dashboardMapper;
    private final DashboardService dashboardService;

    /** 写出双 Sheet 工作簿；调用方负责 OutputStream 生命周期（响应头由 Controller 设置） */
    public void exportLedger(OutputStream out) {
        List<RecycleLedgerRow> ledger = dashboardMapper.selectRecycleLedger();
        List<MonthlyReportRow> monthly = dashboardService.monthlyReport().stream()
                .map(DashboardExportService::toRow)
                .toList();

        try (ExcelWriter writer = EasyExcel.write(out).build()) {
            WriteSheet ledgerSheet = EasyExcel.writerSheet(0, "回收台账").head(RecycleLedgerRow.class).build();
            writer.write(ledger, ledgerSheet);
            WriteSheet monthlySheet = EasyExcel.writerSheet(1, "月度报表").head(MonthlyReportRow.class).build();
            writer.write(monthly, monthlySheet);
        }
    }

    private static MonthlyReportRow toRow(MonthlyReportVO vo) {
        MonthlyReportRow row = new MonthlyReportRow();
        row.setMonth(vo.getMonth());
        row.setRecycleOrders(vo.getRecycleOrders());
        row.setRecycleWeight(vo.getRecycleWeight());
        row.setKitchenBags(vo.getKitchenBags());
        row.setRepairOrders(vo.getRepairOrders());
        row.setCarbonReduced(vo.getCarbonReduced());
        return row;
    }
}

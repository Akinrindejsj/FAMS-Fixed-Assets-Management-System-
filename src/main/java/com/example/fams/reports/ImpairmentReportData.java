package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ImpairmentReportData {
    private LocalDate generatedDate = LocalDate.now();
    private List<ImpairmentRow> rows;
    private int totalRecords;
    private BigDecimal totalImpairmentLoss;
    private BigDecimal totalReversal;
    private BigDecimal netImpairment;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<ImpairmentRow> getRows() {
        return rows;
    }

    public void setRows(List<ImpairmentRow> rows) {
        this.rows = rows;
        this.empty = rows == null || rows.isEmpty();
        this.totalRecords = rows == null ? 0 : rows.size();
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public BigDecimal getTotalImpairmentLoss() {
        return totalImpairmentLoss;
    }

    public void setTotalImpairmentLoss(BigDecimal totalImpairmentLoss) {
        this.totalImpairmentLoss = totalImpairmentLoss;
    }

    public BigDecimal getTotalReversal() {
        return totalReversal;
    }

    public void setTotalReversal(BigDecimal totalReversal) {
        this.totalReversal = totalReversal;
    }

    public BigDecimal getNetImpairment() {
        return netImpairment;
    }

    public void setNetImpairment(BigDecimal netImpairment) {
        this.netImpairment = netImpairment;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DisposalReportData {
    private LocalDate generatedDate = LocalDate.now();
    private List<DisposalRow> rows;
    private int totalRecords;
    private BigDecimal totalProceeds;
    private BigDecimal totalGain;
    private BigDecimal totalLoss;
    private BigDecimal netGainLoss;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<DisposalRow> getRows() {
        return rows;
    }

    public void setRows(List<DisposalRow> rows) {
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

    public BigDecimal getTotalProceeds() {
        return totalProceeds;
    }

    public void setTotalProceeds(BigDecimal totalProceeds) {
        this.totalProceeds = totalProceeds;
    }

    public BigDecimal getTotalGain() {
        return totalGain;
    }

    public void setTotalGain(BigDecimal totalGain) {
        this.totalGain = totalGain;
    }

    public BigDecimal getTotalLoss() {
        return totalLoss;
    }

    public void setTotalLoss(BigDecimal totalLoss) {
        this.totalLoss = totalLoss;
    }

    public BigDecimal getNetGainLoss() {
        return netGainLoss;
    }

    public void setNetGainLoss(BigDecimal netGainLoss) {
        this.netGainLoss = netGainLoss;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class NbvReportData {
    private LocalDate generatedDate = LocalDate.now();
    private List<NbvRow> rows;
    private int totalRecords;
    private BigDecimal totalHistoricalCost;
    private BigDecimal totalAccumulatedDepreciation;
    private BigDecimal totalImpairment;
    private BigDecimal totalNetBookValue;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<NbvRow> getRows() {
        return rows;
    }

    public void setRows(List<NbvRow> rows) {
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

    public BigDecimal getTotalHistoricalCost() {
        return totalHistoricalCost;
    }

    public void setTotalHistoricalCost(BigDecimal totalHistoricalCost) {
        this.totalHistoricalCost = totalHistoricalCost;
    }

    public BigDecimal getTotalAccumulatedDepreciation() {
        return totalAccumulatedDepreciation;
    }

    public void setTotalAccumulatedDepreciation(BigDecimal totalAccumulatedDepreciation) {
        this.totalAccumulatedDepreciation = totalAccumulatedDepreciation;
    }

    public BigDecimal getTotalImpairment() {
        return totalImpairment;
    }

    public void setTotalImpairment(BigDecimal totalImpairment) {
        this.totalImpairment = totalImpairment;
    }

    public BigDecimal getTotalNetBookValue() {
        return totalNetBookValue;
    }

    public void setTotalNetBookValue(BigDecimal totalNetBookValue) {
        this.totalNetBookValue = totalNetBookValue;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
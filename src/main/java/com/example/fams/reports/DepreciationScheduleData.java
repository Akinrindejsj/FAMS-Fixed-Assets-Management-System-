package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DepreciationScheduleData {
    private LocalDate generatedDate = LocalDate.now();
    private List<DepreciationScheduleRow> rows;
    private int totalRecords;
    private BigDecimal totalCost;
    private BigDecimal totalAccumulatedDepreciation;
    private BigDecimal totalCarryingAmount;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<DepreciationScheduleRow> getRows() {
        return rows;
    }

    public void setRows(List<DepreciationScheduleRow> rows) {
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

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getTotalAccumulatedDepreciation() {
        return totalAccumulatedDepreciation;
    }

    public void setTotalAccumulatedDepreciation(BigDecimal totalAccumulatedDepreciation) {
        this.totalAccumulatedDepreciation = totalAccumulatedDepreciation;
    }

    public BigDecimal getTotalCarryingAmount() {
        return totalCarryingAmount;
    }

    public void setTotalCarryingAmount(BigDecimal totalCarryingAmount) {
        this.totalCarryingAmount = totalCarryingAmount;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
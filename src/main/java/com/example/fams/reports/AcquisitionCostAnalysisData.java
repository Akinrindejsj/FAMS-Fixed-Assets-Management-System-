package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


public class AcquisitionCostAnalysisData {
    private LocalDate generatedDate = LocalDate.now();
    private LocalDate fromDate;
    private LocalDate toDate;
    private int totalAssets;
    private BigDecimal totalCost;
    private BigDecimal averageCost;
    private BigDecimal minCost;
    private BigDecimal maxCost;
    private List<AssetSummaryData.CategoryBreakdown> byCategory;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public int getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(int totalAssets) {
        this.totalAssets = totalAssets;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getMinCost() {
        return minCost;
    }

    public void setMinCost(BigDecimal minCost) {
        this.minCost = minCost;
    }

    public BigDecimal getMaxCost() {
        return maxCost;
    }

    public void setMaxCost(BigDecimal maxCost) {
        this.maxCost = maxCost;
    }

    public List<AssetSummaryData.CategoryBreakdown> getByCategory() {
        return byCategory;
    }

    public void setByCategory(List<AssetSummaryData.CategoryBreakdown> byCategory) {
        this.byCategory = byCategory;
    }
}

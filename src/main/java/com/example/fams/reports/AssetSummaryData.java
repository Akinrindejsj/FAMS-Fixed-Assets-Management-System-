package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


public class AssetSummaryData {
    private LocalDate generatedDate = LocalDate.now();
    private int totalAssets;
    private BigDecimal totalHistoricalCost;
    private BigDecimal totalAccumulatedDepreciation;
    private BigDecimal totalNetBookValue;
    private List<CategoryBreakdown> byCategory;

    public record CategoryBreakdown(
            String category,
            int assetCount,
            BigDecimal historicalCost,
            BigDecimal accumulatedDepreciation,
            BigDecimal netBookValue
    ) {
    }

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public int getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(int totalAssets) {
        this.totalAssets = totalAssets;
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

    public BigDecimal getTotalNetBookValue() {
        return totalNetBookValue;
    }

    public void setTotalNetBookValue(BigDecimal totalNetBookValue) {
        this.totalNetBookValue = totalNetBookValue;
    }

    public List<CategoryBreakdown> getByCategory() {
        return byCategory;
    }

    public void setByCategory(List<CategoryBreakdown> byCategory) {
        this.byCategory = byCategory;
    }
}

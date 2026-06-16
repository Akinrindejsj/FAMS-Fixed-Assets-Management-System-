package com.example.fams.depreciation;

import java.math.BigDecimal;
import java.util.List;

public class DepreciationReport {
    private String period;
    private List<DepreciationPosting> postings;
    private BigDecimal totalOriginalCost;
    private BigDecimal totalAccumulatedDepreciation;
    private BigDecimal totalDepreciationCharge;
    private BigDecimal totalBookValue;
    private int assetCount;
    private int fullyDepreciatedCount;

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public List<DepreciationPosting> getPostings() {
        return postings;
    }

    public void setPostings(List<DepreciationPosting> postings) {
        this.postings = postings;
    }

    public BigDecimal getTotalOriginalCost() {
        return totalOriginalCost;
    }

    public void setTotalOriginalCost(BigDecimal totalOriginalCost) {
        this.totalOriginalCost = totalOriginalCost;
    }

    public BigDecimal getTotalAccumulatedDepreciation() {
        return totalAccumulatedDepreciation;
    }

    public void setTotalAccumulatedDepreciation(BigDecimal totalAccumulatedDepreciation) {
        this.totalAccumulatedDepreciation = totalAccumulatedDepreciation;
    }

    public BigDecimal getTotalDepreciationCharge() {
        return totalDepreciationCharge;
    }

    public void setTotalDepreciationCharge(BigDecimal totalDepreciationCharge) {
        this.totalDepreciationCharge = totalDepreciationCharge;
    }

    public BigDecimal getTotalBookValue() {
        return totalBookValue;
    }

    public void setTotalBookValue(BigDecimal totalBookValue) {
        this.totalBookValue = totalBookValue;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public int getFullyDepreciatedCount() {
        return fullyDepreciatedCount;
    }

    public void setFullyDepreciatedCount(int fullyDepreciatedCount) {
        this.fullyDepreciatedCount = fullyDepreciatedCount;
    }
}


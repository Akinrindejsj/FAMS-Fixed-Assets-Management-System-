package com.example.fams.depreciation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DepreciationDashboardData {
    private LocalDateTime lastCalculatedAt;
    private String lastCalculatedPeriod;
    private List<DepreciationPosting> latestPostings;
    private DepreciationSummary summary;

    public DepreciationDashboardData() {}

    public DepreciationDashboardData(LocalDateTime lastCalculatedAt, String lastCalculatedPeriod,
                                    List<DepreciationPosting> latestPostings, DepreciationSummary summary) {
        this.lastCalculatedAt = lastCalculatedAt;
        this.lastCalculatedPeriod = lastCalculatedPeriod;
        this.latestPostings = latestPostings;
        this.summary = summary;
    }

    public LocalDateTime getLastCalculatedAt() {
        return lastCalculatedAt;
    }

    public void setLastCalculatedAt(LocalDateTime lastCalculatedAt) {
        this.lastCalculatedAt = lastCalculatedAt;
    }

    public String getLastCalculatedPeriod() {
        return lastCalculatedPeriod;
    }

    public void setLastCalculatedPeriod(String lastCalculatedPeriod) {
        this.lastCalculatedPeriod = lastCalculatedPeriod;
    }

    public List<DepreciationPosting> getLatestPostings() {
        return latestPostings;
    }

    public void setLatestPostings(List<DepreciationPosting> latestPostings) {
        this.latestPostings = latestPostings;
    }

    public DepreciationSummary getSummary() {
        return summary;
    }

    public void setSummary(DepreciationSummary summary) {
        this.summary = summary;
    }
}


package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class GroupedReportData {
    private String reportName;
    private AssetGroupBy groupBy;
    private LocalDate generatedDate = LocalDate.now();
    private List<AssetGroup> groups;
    private int totalRecords;
    private BigDecimal totalValue;
    private BigDecimal totalDepreciation;
    private BigDecimal totalNetBookValue;
    private boolean empty = true;

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public AssetGroupBy getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(AssetGroupBy groupBy) {
        this.groupBy = groupBy;
    }

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<AssetGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<AssetGroup> groups) {
        this.groups = groups;
        this.empty = groups == null || groups.isEmpty();
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getTotalDepreciation() {
        return totalDepreciation;
    }

    public void setTotalDepreciation(BigDecimal totalDepreciation) {
        this.totalDepreciation = totalDepreciation;
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

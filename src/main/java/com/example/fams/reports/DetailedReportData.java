package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


public class DetailedReportData {
    private String reportType;
    private String reportName;
    private LocalDate generatedDate;
    private String category;
    private String department;
    private String status;
    private List<DetailedAssetRow> rows;
    private boolean empty;
    private int totalRecords;
    private BigDecimal totalValue;
    private BigDecimal totalDepreciation;
    private BigDecimal totalNetBookValue;

    public DetailedReportData() {
        this.generatedDate = LocalDate.now();
        this.empty = true;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<DetailedAssetRow> getRows() {
        return rows;
    }

    public void setRows(List<DetailedAssetRow> rows) {
        this.rows = rows;
        this.empty = rows == null || rows.isEmpty();
        this.totalRecords = rows == null ? 0 : rows.size();
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
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
}

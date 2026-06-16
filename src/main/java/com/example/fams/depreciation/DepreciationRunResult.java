package com.example.fams.depreciation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class DepreciationRunResult {
    private String period;
    private LocalDate runDate;
    private String status;
    private String errorMessage;
    private List<String> successfulAssets = new ArrayList<>();
    private Map<String, String> failedAssets = new HashMap<>();

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getSuccessfulAssets() {
        return successfulAssets;
    }

    public void setSuccessfulAssets(List<String> successfulAssets) {
        this.successfulAssets = successfulAssets;
    }

    public Map<String, String> getFailedAssets() {
        return failedAssets;
    }

    public void setFailedAssets(Map<String, String> failedAssets) {
        this.failedAssets = failedAssets;
    }

    public void addSuccessfulAsset(String assetCode) {
        this.successfulAssets.add(assetCode);
    }

    public void addFailedAsset(String assetCode, String error) {
        this.failedAssets.put(assetCode, error);
    }

    public int getProcessedCount() {
        return successfulAssets.size() + (failedAssets != null ? failedAssets.size() : 0);
    }

    public int getSuccessCount() {
        return successfulAssets.size();
    }

    public int getFailureCount() {
        return failedAssets != null ? failedAssets.size() : 0;
    }
}



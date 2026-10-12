package com.example.fams.depreciation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Section 4, item 11 — Depreciation Run Report: a persisted record of each depreciation
 * batch run. Previously {@link DepreciationRunResult} was computed and returned once in
 * the HTTP response of triggering a run, then discarded — there was no way to answer
 * "show me past runs" after the fact. This entity is written alongside the postings
 * every time {@link DepreciationService#runDepreciation} executes.
 *
 * <p>Failure detail is kept as a simple semicolon-joined "code: reason" string rather
 * than a child table — this is a read-only audit trail, not something queried per
 * individual failure, so the extra relational structure wouldn't earn its keep.
 */
@Entity
@Table(name = "depreciation_runs")
public class DepreciationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String period;

    private LocalDate periodEndDate;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(length = 160)
    private String triggeredBy;

    @Column(nullable = false)
    private LocalDateTime triggeredAt;

    private int processedCount;
    private int successCount;
    private int failureCount;

    @Column(columnDefinition = "text")
    private String successfulAssetCodes;

    /** "CODE: reason; CODE: reason; ..." */
    @Column(columnDefinition = "text")
    private String failedAssetDetails;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDate getPeriodEndDate() {
        return periodEndDate;
    }

    public void setPeriodEndDate(LocalDate periodEndDate) {
        this.periodEndDate = periodEndDate;
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

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public String getSuccessfulAssetCodes() {
        return successfulAssetCodes;
    }

    public void setSuccessfulAssetCodes(String successfulAssetCodes) {
        this.successfulAssetCodes = successfulAssetCodes;
    }

    public String getFailedAssetDetails() {
        return failedAssetDetails;
    }

    public void setFailedAssetDetails(String failedAssetDetails) {
        this.failedAssetDetails = failedAssetDetails;
    }
}
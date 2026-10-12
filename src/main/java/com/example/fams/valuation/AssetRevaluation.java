package com.example.fams.valuation;

import com.example.fams.assets.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Section 5, items 9–11 — Revaluation Report / History / Gain-Loss Report all read from
 * this same table; they differ only in framing (a period, full history, or a totals
 * summary), not in underlying data.
 *
 * <p>{@code previousCarryingAmount} is captured automatically at creation time from the
 * asset's current position (see AssetRevaluationService), not typed by the user — this
 * keeps the gain/loss calculation trustworthy rather than dependent on someone typing
 * the right "before" figure.
 */
@Entity
@Table(name = "asset_revaluations")
public class AssetRevaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal previousCarryingAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal revaluedAmount;

    /** revaluedAmount - previousCarryingAmount. Positive = gain (write-up), negative = loss (write-down). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal gainLoss;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(length = 160)
    private String recordedBy;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getPreviousCarryingAmount() {
        return previousCarryingAmount;
    }

    public void setPreviousCarryingAmount(BigDecimal previousCarryingAmount) {
        this.previousCarryingAmount = previousCarryingAmount;
    }

    public BigDecimal getRevaluedAmount() {
        return revaluedAmount;
    }

    public void setRevaluedAmount(BigDecimal revaluedAmount) {
        this.revaluedAmount = revaluedAmount;
    }

    public BigDecimal getGainLoss() {
        return gainLoss;
    }

    public void setGainLoss(BigDecimal gainLoss) {
        this.gainLoss = gainLoss;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
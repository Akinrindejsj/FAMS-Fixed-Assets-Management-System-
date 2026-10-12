package com.example.fams.valuation;

import com.example.fams.assets.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Section 5, items 1 (partial), 12–13 — Impairment Report / History both read from this
 * table (same framing distinction as {@link AssetRevaluation}: a period vs full history).
 * Also feeds the impairment column on the Net Book Value Report (item 1).
 *
 * <p>{@code amount} is always a positive magnitude; {@code type} determines direction.
 * {@code carryingAmountBefore} is captured automatically at creation time, same rationale
 * as AssetRevaluation.
 */
@Entity
@Table(name = "asset_impairments")
public class AssetImpairment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImpairmentType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal carryingAmountBefore;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal carryingAmountAfter;

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

    public ImpairmentType getType() {
        return type;
    }

    public void setType(ImpairmentType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getCarryingAmountBefore() {
        return carryingAmountBefore;
    }

    public void setCarryingAmountBefore(BigDecimal carryingAmountBefore) {
        this.carryingAmountBefore = carryingAmountBefore;
    }

    public BigDecimal getCarryingAmountAfter() {
        return carryingAmountAfter;
    }

    public void setCarryingAmountAfter(BigDecimal carryingAmountAfter) {
        this.carryingAmountAfter = carryingAmountAfter;
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
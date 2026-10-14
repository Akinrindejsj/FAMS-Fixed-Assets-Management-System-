package com.example.fams.gl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A journal that should have been generated from an asset transaction but wasn't —
 * missing account mapping, unbalanced entry, or a conflict with an already-posted
 * journal. Recorded so the Journal Exception Report can show gaps that would otherwise
 * be silent.
 */
@Entity
@Table(name = "journal_generation_failures")
public class JournalGenerationFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JournalSourceType sourceType;

    private Long sourceReferenceId;

    @Column(length = 64)
    private String assetCode;

    @Column(length = 32)
    private String period;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public JournalSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(JournalSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceReferenceId() {
        return sourceReferenceId;
    }

    public void setSourceReferenceId(Long sourceReferenceId) {
        this.sourceReferenceId = sourceReferenceId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

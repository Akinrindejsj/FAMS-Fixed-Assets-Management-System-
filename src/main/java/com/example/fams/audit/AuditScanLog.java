package com.example.fams.audit;

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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One entry per verification submission. The verification result keeps only the latest
 * outcome per asset per session, so this log preserves every scan, including re-scans.
 */
@Entity
@Table(name = "audit_scan_logs")
public class AuditScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuditSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false, length = 80)
    private String scannedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScanMethod scanMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditVerificationStatus resultStatus;

    @Column(length = 160)
    private String scannedBy;

    @Column(nullable = false)
    private LocalDateTime scannedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public AuditSession getSession() {
        return session;
    }

    public void setSession(AuditSession session) {
        this.session = session;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public ScanMethod getScanMethod() {
        return scanMethod;
    }

    public void setScanMethod(ScanMethod scanMethod) {
        this.scanMethod = scanMethod;
    }

    public AuditVerificationStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(AuditVerificationStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getScannedBy() {
        return scannedBy;
    }

    public void setScannedBy(String scannedBy) {
        this.scannedBy = scannedBy;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(LocalDateTime scannedAt) {
        this.scannedAt = scannedAt;
    }
}

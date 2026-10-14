package com.example.fams.audit;

import com.example.fams.assets.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An asset that was expected to be verified in a session, captured when the session
 * started. Gives the count, unverified and reconciliation reports a fixed population that
 * isn't skewed by assets moved or disposed after the audit began.
 */
@Entity
@Table(
        name = "audit_session_expected_assets",
        uniqueConstraints = @UniqueConstraint(name = "uk_audit_expected_session_asset", columnNames = {"session_id", "asset_id"})
)
public class AuditSessionExpectedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuditSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(length = 260)
    private String expectedLocation;

    @Column(length = 160)
    private String expectedCustodian;

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

    public String getExpectedLocation() {
        return expectedLocation;
    }

    public void setExpectedLocation(String expectedLocation) {
        this.expectedLocation = expectedLocation;
    }

    public String getExpectedCustodian() {
        return expectedCustodian;
    }

    public void setExpectedCustodian(String expectedCustodian) {
        this.expectedCustodian = expectedCustodian;
    }
}

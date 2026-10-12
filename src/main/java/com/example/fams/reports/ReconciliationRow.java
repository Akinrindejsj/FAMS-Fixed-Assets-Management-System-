package com.example.fams.reports;

import java.math.BigDecimal;

/**
 * Section 8, items 6-9 — one line of the asset-subledger-vs-GL reconciliation.
 * {@code subledgerBalance} comes from the asset/depreciation data directly;
 * {@code glBalance} comes from POSTED journal lines only — the difference is typically
 * value sitting in UNPOSTED journals, not a real error, unless it persists after posting.
 */
public record ReconciliationRow(
        String accountRole,
        String accountCode,
        BigDecimal subledgerBalance,
        BigDecimal glBalance,
        BigDecimal difference
) {
}
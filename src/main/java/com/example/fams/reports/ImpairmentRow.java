package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Section 5, items 12–13 — one impairment/reversal entry, with asset context. */
public record ImpairmentRow(
        LocalDate date,
        String assetCode,
        String assetName,
        String category,
        String department,
        String type,
        BigDecimal amount,
        BigDecimal carryingAmountBefore,
        BigDecimal carryingAmountAfter,
        String reason,
        String recordedBy
) {
}
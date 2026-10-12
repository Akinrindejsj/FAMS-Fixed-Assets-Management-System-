package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Section 5, items 9–11 — one revaluation entry, with asset context for reporting. */
public record RevaluationRow(
        LocalDate date,
        String assetCode,
        String assetName,
        String category,
        String department,
        BigDecimal previousCarryingAmount,
        BigDecimal revaluedAmount,
        BigDecimal gainLoss,
        String reason,
        String recordedBy
) {
}
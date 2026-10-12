package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Section 4, item 14 — one historical posting, flattened across all scoped assets. */
public record HistoryRow(
        String assetCode,
        String assetName,
        String period,
        BigDecimal depreciationCharge,
        BigDecimal closingAccumulatedDepreciation,
        BigDecimal bookValue,
        boolean fullyDepreciated
) {
}
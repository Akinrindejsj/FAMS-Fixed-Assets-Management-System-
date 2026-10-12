package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One historical posting, flattened across all scoped assets. */
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
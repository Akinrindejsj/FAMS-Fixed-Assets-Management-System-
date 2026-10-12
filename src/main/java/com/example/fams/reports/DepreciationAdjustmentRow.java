package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Section 4, item 13 — one recorded depreciation correction/reversal/recalculation. */
public record DepreciationAdjustmentRow(
        LocalDate date,
        String assetCode,
        String assetName,
        String category,
        String department,
        String period,
        String type,
        BigDecimal amount,
        String reason,
        String recordedBy
) {
}
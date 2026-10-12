package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One capital addition/adjustment, with asset context for reporting. */
public record CapitalAdjustmentRow(
        LocalDate date,
        String assetCode,
        String assetName,
        String category,
        String department,
        String type,
        BigDecimal amount,
        String description,
        String recordedBy
) {
}

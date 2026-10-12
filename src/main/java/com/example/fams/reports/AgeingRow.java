package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Asset age (from purchaseDate) alongside its current value. */
public record AgeingRow(
        String assetCode,
        String assetName,
        String category,
        LocalDate purchaseDate,
        int ageYears,
        String ageBand,
        BigDecimal cost,
        BigDecimal netBookValue
) {
}
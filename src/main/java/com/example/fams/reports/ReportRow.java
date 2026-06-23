package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportRow(
        Long assetId,
        String assetCode,
        String assetName,
        String category,
        String department,
        String custodian,
        LocalDate acquisitionDate,
        BigDecimal acquisitionCost,
        BigDecimal accumulatedDepreciation,
        BigDecimal netBookValue,
        String status
) {
}


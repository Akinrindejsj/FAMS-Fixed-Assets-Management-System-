package com.example.fams.reports;

import java.math.BigDecimal;

/** Aggregate posted activity for one GL account. */
public record AccountSummaryRow(
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal netMovement
) {
}
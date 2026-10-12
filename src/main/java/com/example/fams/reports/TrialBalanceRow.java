package com.example.fams.reports;

import java.math.BigDecimal;

/** One account's trial-balance line (posted activity only). */
public record TrialBalanceRow(
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal debitBalance,
        BigDecimal creditBalance
) {
}
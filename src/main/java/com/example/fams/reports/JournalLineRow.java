package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One journal line, with its parent entry's context. */
public record JournalLineRow(
        String journalNumber,
        LocalDate date,
        String sourceType,
        String status,
        String accountCode,
        String accountName,
        String assetCode,
        String assetName,
        BigDecimal debit,
        BigDecimal credit,
        String description
) {
}
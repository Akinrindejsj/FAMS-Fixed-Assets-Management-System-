package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One journal entry header. */
public record JournalEntryRow(
        String journalNumber,
        LocalDate date,
        String period,
        String sourceType,
        String status,
        String description,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        String postedBy,
        String rejectionReason
) {
}
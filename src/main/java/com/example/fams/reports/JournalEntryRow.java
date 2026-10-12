package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Section 8, items 2, 3, 4, 12 — one journal entry header. */
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
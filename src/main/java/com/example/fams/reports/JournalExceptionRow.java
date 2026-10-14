package com.example.fams.reports;

import java.time.LocalDate;

/** One journal problem: a rejected or unbalanced entry, or a journal that could not be generated. */
public record JournalExceptionRow(
        String exceptionType,
        String reference,
        LocalDate date,
        String sourceType,
        String assetCode,
        String reason
) {
}

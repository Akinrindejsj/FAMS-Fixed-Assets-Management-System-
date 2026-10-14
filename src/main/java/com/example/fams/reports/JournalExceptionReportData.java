package com.example.fams.reports;

import java.time.LocalDate;
import java.util.List;

/** Journal Exception Report: rejected and unbalanced journals plus failed generations. */
public record JournalExceptionReportData(
        LocalDate generatedDate,
        List<JournalExceptionRow> rows,
        boolean empty
) {
}
